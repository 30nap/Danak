package ir.danak.app.data

import android.content.Context
import android.graphics.BitmapFactory
import ir.danak.app.model.Danak
import ir.danak.app.model.DanakImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Where the app's Danaks come from: the pack bundled in the APK, and published content
 * downloaded into [cache].
 *
 * The bundle is the baseline and is never touched. Published content is an enhancement:
 * it is used only once every file of an index has been downloaded and verified, and any
 * failure leaves the previous state in place. When a published set is active it defines
 * the feed — a Danak removed from the index leaves the feed, which is how a mistake gets
 * retracted — while the bundle stays in the APK as the fallback.
 */
class ContentRepository(
    private val loadBundled: suspend () -> List<Danak>,
    private val cache: ContentCache,
    /** Null switches published content off: the app runs on the bundle alone. */
    private val fetcher: ContentFetcher?,
    /** Whether a stored image file actually decodes. */
    private val imageDecodes: (File) -> Boolean,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    sealed interface Refresh {
        /** A new published set was verified and activated. */
        class Updated(val danaks: List<Danak>) : Refresh

        /** Nothing new, or published content is switched off. */
        data object UpToDate : Refresh

        /** Nothing changed; the reason is for logs and tests. */
        class Failed(val reason: String) : Refresh
    }

    private val lock = Mutex()
    private var bundled: List<Danak>? = null

    private suspend fun bundled(): List<Danak> = bundled ?: loadBundled().also { bundled = it }

    /**
     * What to show right now, from disk only: the active published set if it is intact,
     * the bundle otherwise. Never touches the network, never throws.
     */
    suspend fun loadLocal(): List<Danak> = lock.withLock {
        withContext(io) {
            val bundled = bundled()
            val active = cache.activeIndex() ?: return@withContext bundled
            val assembled = try {
                assemble(RemoteIndex.parse(active), bundled).danaks
            } catch (e: InvalidContentException) {
                null
            } catch (e: IOException) {
                null
            }
            if (assembled.isNullOrEmpty()) {
                // A damaged cache must not stick: forget it, and the next refresh
                // downloads what is missing.
                cache.deactivate()
                bundled
            } else {
                assembled
            }
        }
    }

    /** Checks the published site once and activates a new set if there is a valid one. */
    suspend fun refresh(): Refresh = lock.withLock {
        withContext(io) {
            val fetcher = fetcher ?: return@withContext Refresh.UpToDate
            try {
                update(fetcher)
            } catch (e: InvalidContentException) {
                Refresh.Failed("invalid: ${e.message}")
            } catch (e: IOException) {
                Refresh.Failed("network: ${e.message}")
            }
        }
    }

    private suspend fun update(fetcher: ContentFetcher): Refresh {
        val active = cache.activeIndex()
        val result = fetcher.fetchIndex(if (active != null) cache.etag() else null)
        if (result !is ContentFetcher.IndexResult.Fetched) return Refresh.UpToDate
        if (active != null && active.contentEquals(result.bytes)) {
            cache.saveEtag(result.etag)
            return Refresh.UpToDate
        }
        val entries = RemoteIndex.parse(result.bytes)

        // 1. Danak files: only the ones not already stored. Each is checked against the
        //    index hash before it is written, so a stored object is always a verified one.
        for (entry in entries) {
            // A stored file is trusted only while it still matches its hash: one damaged on
            // disk is fetched again instead of blocking every future update.
            if (cache.hasObject(entry.sha256) && sha256(cache.objectFile(entry.sha256).readBytes()) == entry.sha256) continue
            val body = fetcher.fetchFile(entry.path, ContentFetcher.MAX_DANAK_BYTES)
                ?: throw InvalidContentException("${entry.path} is missing")
            if (sha256(body) != entry.sha256) throw InvalidContentException("${entry.path} does not match its hash")
            cache.writeObject(entry.sha256, body)
        }

        // 2. Photos. A photo that is missing or broken on the server costs that Danak its
        //    photo, not the update; a network failure stops the update (and is retried).
        for (entry in entries) {
            val name = imageNameOf(entry) ?: continue
            if (cache.hasImage(name)) continue
            val bytes = fetcher.fetchFile("images/$name", ContentFetcher.MAX_IMAGE_BYTES) ?: continue
            val hash8 = name.substringAfterLast('_').substringBefore('.')
            if (!sha256(bytes).startsWith(hash8) || !looksLikeImage(bytes, name.substringAfterLast('.'))) continue
            cache.writeImage(name, bytes, imageDecodes)
        }

        // 3. Everything is on disk and verified: switch over in one step.
        val assembled = assemble(entries, bundled())
        if (assembled.danaks.isEmpty()) throw InvalidContentException("no usable Danak in the index")
        cache.activate(result.bytes, result.etag)
        cache.retainOnly(entries.map { it.sha256 }.toSet(), assembled.imageNames)
        return Refresh.Updated(assembled.danaks)
    }

    private class Assembled(val danaks: List<Danak>, val imageNames: Set<String>)

    /**
     * Builds the feed for an index from stored files. Throws if a file the index needs is
     * missing or does not match its hash — the set is then not usable as a whole.
     */
    private fun assemble(entries: List<IndexEntry>, bundled: List<Danak>): Assembled {
        val bundledById = bundled.associateBy { it.id }
        val danaks = mutableListOf<Danak>()
        val imageNames = mutableSetOf<String>()
        for (entry in entries) {
            val file = cache.objectFile(entry.sha256)
            if (!file.isFile) throw InvalidContentException("${entry.id} is not stored")
            val bytes = file.readBytes()
            if (sha256(bytes) != entry.sha256) throw InvalidContentException("${entry.id} is damaged on disk")

            val parsed = ContentPack.parseDanak(bytes.decodeToString()) { src ->
                val name = imageName(entry.id, src)
                if (name != null && cache.hasImage(name)) {
                    imageNames += name
                    DanakImage.Cached(cache.imageFile(name).path)
                } else {
                    DanakImage.None
                }
            }
            val fallback = bundledById[entry.id]
            val danak = when {
                // An invalid published version never replaces a valid bundled one.
                parsed == null || parsed.id != entry.id -> fallback
                // Photo unusable (or hosted elsewhere): keep the bundled photo if there is one.
                parsed.image !is DanakImage.Cached -> parsed.copy(image = fallback?.image ?: DanakImage.None)
                else -> parsed
            }
            danak?.let(danaks::add)
        }
        return Assembled(ContentPack.interleaveByCategory(danaks), imageNames)
    }

    private fun imageNameOf(entry: IndexEntry): String? {
        var name: String? = null
        ContentPack.parseDanak(cache.objectFile(entry.sha256).readText()) { src ->
            name = imageName(entry.id, src)
            DanakImage.None
        } ?: return null
        return name
    }

    companion object {
        /** Published photos are `images/<id>_<hash8>.<ext>`, named for their own Danak. */
        private fun imageName(id: String, src: String): String? =
            Regex("images/(${Regex.escape(id)}_[0-9a-f]{8}\\.(webp|jpg|png))").matchEntire(src)?.groupValues?.get(1)

        internal fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        internal fun looksLikeImage(bytes: ByteArray, ext: String): Boolean = when (ext) {
            "webp" -> bytes.size > 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
                bytes.copyOfRange(8, 12).decodeToString() == "WEBP"
            "jpg" -> bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
            "png" -> bytes.size > 8 && bytes.copyOfRange(1, 4).decodeToString() == "PNG"
            else -> false
        }

        /** The app's repository: bundled content plus the published site, if enabled. */
        fun create(context: Context): ContentRepository {
            val app = context.applicationContext
            return ContentRepository(
                loadBundled = { BundledContent.load(app) },
                // Re-downloadable, so kept out of backups.
                cache = ContentCache(File(app.noBackupFilesDir, "content/v1")),
                fetcher = RemoteContentSettings.config?.let { ContentFetcher(it) },
                imageDecodes = ::decodesAsBitmap,
            )
        }

        private fun decodesAsBitmap(file: File): Boolean {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, options)
            return options.outWidth > 0 && options.outHeight > 0
        }
    }
}

/**
 * Where the app looks for published content. Instrumented tests point it at a local
 * server, or switch it off with null, before the activity starts.
 */
object RemoteContentSettings {
    @Volatile
    var config: RemoteConfig? = RemoteConfig.Production
}
