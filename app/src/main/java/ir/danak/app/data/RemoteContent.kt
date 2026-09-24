package ir.danak.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Where published content lives. Every URL the app requests is [baseUrl] plus a path that
 * has been checked against a fixed pattern, so no index entry can point the app anywhere
 * else.
 */
class RemoteConfig(val baseUrl: String, requireHttps: Boolean = true) {
    init {
        require(baseUrl.endsWith("/v1/")) { "base URL must end in /v1/" }
        require(!requireHttps || baseUrl.startsWith("https://")) { "published content is https only" }
    }

    companion object {
        val Production = RemoteConfig("https://30nap.github.io/Danak/v1/")
    }
}

/** Anything published that the app refuses to use. Never activated, never retried as-is. */
class InvalidContentException(message: String) : Exception(message)

/** A request that failed in a way that may succeed later (offline, timeout, 5xx). */
class TransientFetchException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** One line of `v1/index.json`. */
data class IndexEntry(val id: String, val path: String, val sha256: String)

/** Reads `v1/index.json` (schema/index-v1.schema.json) as untrusted input. */
object RemoteIndex {

    const val MAX_ENTRIES = 5_000
    private val ID = Regex("[a-z0-9]+(_[a-z0-9]+)*")
    private val SHA256 = Regex("[0-9a-f]{64}")
    private val json = Json

    /** Every entry, in feed order; throws [InvalidContentException] for anything off-contract. */
    fun parse(bytes: ByteArray): List<IndexEntry> {
        val root = runCatching { json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)) }
            .getOrNull() as? JsonObject ?: invalid("index is not a JSON object")
        if ((root["schemaVersion"] as? JsonPrimitive)?.intOrNull != 1) invalid("unsupported schemaVersion")
        val array = root["danaks"] as? JsonArray ?: invalid("danaks is not a list")
        // An empty feed is never what a publish meant; keep what the app already has.
        if (array.isEmpty()) invalid("index is empty")
        if (array.size > MAX_ENTRIES) invalid("index has too many entries")

        val entries = array.map { element ->
            val obj = element as? JsonObject ?: invalid("entry is not an object")
            fun field(name: String) = (obj[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: invalid("entry without $name")
            val entry = IndexEntry(field("id"), field("path"), field("sha256"))
            if (!ID.matches(entry.id) || entry.id.length > 64) invalid("bad id ${entry.id}")
            if (!SHA256.matches(entry.sha256)) invalid("bad sha256 for ${entry.id}")
            // The path is fully determined by id and hash; anything else is refused, which
            // also rules out absolute URLs, other hosts, schemes and ../ in one check.
            if (entry.path != "content/${entry.id}_${entry.sha256.take(8)}.json") invalid("unexpected path ${entry.path}")
            entry
        }
        if (entries.map { it.id }.toSet().size != entries.size) invalid("duplicate id in index")
        return entries
    }

    private fun invalid(message: String): Nothing = throw InvalidContentException(message)
}

/**
 * Plain GETs against the published site, with hard size limits. It keeps no HTTP cache of
 * its own: files are content-addressed and verified by hash, and the index is compared by
 * ETag and by bytes, so a cache would only add another copy on disk.
 */
class ContentFetcher(
    private val config: RemoteConfig,
    private val client: OkHttpClient = defaultClient(),
) {
    sealed interface IndexResult {
        data object NotModified : IndexResult
        class Fetched(val bytes: ByteArray, val etag: String?) : IndexResult
    }

    fun fetchIndex(etag: String?): IndexResult {
        val request = Request.Builder().url(config.baseUrl + "index.json")
            .apply { if (etag != null) header("If-None-Match", etag) }
            .build()
        return execute(request, MAX_INDEX_BYTES) { code, body, headers ->
            if (code == 304 && etag != null) IndexResult.NotModified
            else IndexResult.Fetched(body ?: throw TransientFetchException("index: empty body"), headers["ETag"])
        }
    }

    /**
     * Fetches `v1/<path>`. [path] must already have passed the index or image pattern checks.
     * Returns null when the file is not there (404/410): a publishing fault, not a network one.
     */
    fun fetchFile(path: String, maxBytes: Long): ByteArray? {
        require(!path.contains("..") && !path.contains(":") && !path.startsWith("/")) { "unsafe path $path" }
        val request = Request.Builder().url(config.baseUrl + path).build()
        return execute(request, maxBytes) { code, body, _ -> if (code == 404 || code == 410) null else body }
    }

    private fun <T> execute(
        request: Request,
        maxBytes: Long,
        handle: (code: Int, body: ByteArray?, headers: okhttp3.Headers) -> T,
    ): T {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw TransientFetchException("${request.url.encodedPath}: ${e.message}", e)
        }
        response.use {
            val code = it.code
            when {
                code == 304 || code == 404 || code == 410 -> return handle(code, null, it.headers)
                code in 500..599 || code == 429 || code == 408 -> throw TransientFetchException("${request.url.encodedPath}: HTTP $code")
                code != 200 -> throw InvalidContentException("${request.url.encodedPath}: HTTP $code")
            }
            val body = it.body ?: throw TransientFetchException("${request.url.encodedPath}: no body")
            if (body.contentLength() > maxBytes) throw InvalidContentException("${request.url.encodedPath}: larger than $maxBytes bytes")
            val bytes = try {
                // Read one byte past the limit, so an unannounced oversized body is caught too.
                val source = body.source()
                source.request(maxBytes + 1)
                if (source.buffer.size > maxBytes) throw InvalidContentException("${request.url.encodedPath}: larger than $maxBytes bytes")
                source.readByteArray()
            } catch (e: IOException) {
                // A connection that drops half-way leaves a partial body: never used.
                throw TransientFetchException("${request.url.encodedPath}: ${e.message}", e)
            }
            return handle(code, bytes, it.headers)
        }
    }

    companion object {
        const val MAX_INDEX_BYTES = 512L * 1024
        const val MAX_DANAK_BYTES = 64L * 1024
        const val MAX_IMAGE_BYTES = 2L * 1024 * 1024

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            // No cookies, no cache, nothing that identifies the device: just the files.
            .retryOnConnectionFailure(true)
            // A redirect could lead to another host; published files never need one.
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
