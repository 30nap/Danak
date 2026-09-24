package ir.danak.app.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Downloaded content in app-private storage:
 *
 * ```
 * <root>/
 * ├── index.json          the active index: the last one whose every file was verified
 * ├── etag                its ETag, if the server sent one
 * ├── objects/<sha256>.json   Danak files, named by the hash of their bytes
 * ├── images/<id>_<hash8>.<ext>
 * └── tmp/                in-flight writes
 * ```
 *
 * Objects and images are immutable and only ever appear under their final name after
 * being verified (write to tmp, fsync, atomic rename). The active set changes in one step,
 * the rename of `index.json`, so a crash at any point leaves either the old set or the new
 * one — never a mix, never a half-written file.
 */
class ContentCache(private val root: File) {

    private val objects = File(root, "objects")
    private val images = File(root, "images")
    private val tmp = File(root, "tmp")
    private val index = File(root, "index.json")
    private val etag = File(root, "etag")

    fun activeIndex(): ByteArray? = index.takeIf { it.isFile }?.readBytes()

    fun etag(): String? = etag.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }

    fun objectFile(sha256: String) = File(objects, "$sha256.json")

    fun hasObject(sha256: String) = objectFile(sha256).isFile

    fun writeObject(sha256: String, bytes: ByteArray) = atomicWrite(objectFile(sha256), bytes)

    fun imageFile(name: String) = File(images, name)

    fun hasImage(name: String) = imageFile(name).isFile

    /** Writes an image only if [isValid] accepts the written file; returns whether it did. */
    fun writeImage(name: String, bytes: ByteArray, isValid: (File) -> Boolean): Boolean {
        val staged = stage(bytes)
        return if (isValid(staged)) {
            move(staged, imageFile(name))
            true
        } else {
            staged.delete()
            false
        }
    }

    /** Makes [indexBytes] the active set. Everything it lists must already be stored. */
    fun activate(indexBytes: ByteArray, newEtag: String?) {
        atomicWrite(index, indexBytes)
        if (newEtag != null) atomicWrite(etag, newEtag.toByteArray()) else etag.delete()
    }

    fun saveEtag(newEtag: String?) {
        if (newEtag != null) atomicWrite(etag, newEtag.toByteArray())
    }

    /** Forgets the active set (the app goes back to bundled content) but keeps objects. */
    fun deactivate() {
        index.delete()
        etag.delete()
    }

    /** Deletes stored files no longer referenced by the active set, and stale temp files. */
    fun retainOnly(objectShas: Set<String>, imageNames: Set<String>) {
        objects.listFiles()?.filter { it.name.removeSuffix(".json") !in objectShas }?.forEach { it.delete() }
        images.listFiles()?.filter { it.name !in imageNames }?.forEach { it.delete() }
        tmp.listFiles()?.forEach { it.delete() }
    }

    /** Total bytes on disk, for measurements. */
    fun sizeOnDisk(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun atomicWrite(target: File, bytes: ByteArray) = move(stage(bytes), target)

    private fun stage(bytes: ByteArray): File {
        tmp.mkdirs()
        val staged = File.createTempFile("part", ".tmp", tmp)
        FileOutputStream(staged).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        return staged
    }

    private fun move(staged: File, target: File) {
        target.parentFile?.mkdirs()
        Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
