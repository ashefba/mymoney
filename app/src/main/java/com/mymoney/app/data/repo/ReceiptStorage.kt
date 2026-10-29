package com.mymoney.app.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Stores receipt photos in app-private storage. Nothing ever leaves the device. */
class ReceiptStorage(private val context: Context) {
    val dir: File get() = File(context.filesDir, "receipts").apply { mkdirs() }

    fun isValidName(name: String): Boolean = RECEIPT_NAME.matches(name)

    fun file(name: String): File {
        require(isValidName(name)) { "Invalid receipt file name." }
        val root = dir.canonicalFile
        val candidate = File(root, name).canonicalFile
        require(candidate.parentFile == root) { "Receipt path escapes private storage." }
        return candidate
    }

    /** Copies, downsizes and re-encodes an image picked by the user. Returns the stored file name. */
    fun saveFromUri(uri: Uri, maxDimension: Int = 1600): String? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDimension || bounds.outHeight / (sample * 2) >= maxDimension) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (rotation != 0f) {
            val m = Matrix().apply { postRotate(rotation) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            if (rotated != bitmap) bitmap.recycle()
            bitmap = rotated
        }
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest > maxDimension) {
            val scale = maxDimension.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            if (scaled != bitmap) bitmap.recycle()
            bitmap = scaled
        }
        val name = "receipt_${UUID.randomUUID()}.jpg"
        FileOutputStream(file(name)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        return name
    }

    fun delete(name: String?) {
        if (name.isNullOrBlank() || !isValidName(name)) return
        file(name).delete()
    }

    fun read(name: String): ByteArray? =
        if (isValidName(name)) file(name).takeIf { it.exists() }?.readBytes() else null

    fun write(name: String, bytes: ByteArray) {
        require(isValidName(name)) { "Invalid receipt file name." }
        file(name).writeBytes(bytes)
    }

    /** Validates and writes restored photos away from the live receipt directory. */
    fun stageRestore(receipts: Map<String, ByteArray>): File {
        val staging = File(context.cacheDir, "receipt_restore_${UUID.randomUUID()}")
        check(staging.mkdirs()) { "Could not prepare receipt restore." }
        try {
            receipts.forEach { (name, bytes) ->
                require(isValidName(name)) { "Backup contains an invalid receipt name." }
                require(bytes.isNotEmpty()) { "Backup contains an empty receipt image." }
                File(staging, name).writeBytes(bytes)
            }
            return staging
        } catch (e: Exception) {
            staging.deleteRecursively()
            throw e
        }
    }

    /** Replaces all live photos with a previously validated staging directory. */
    fun commitStagedRestore(staging: File) {
        require(staging.parentFile?.canonicalFile == context.cacheDir.canonicalFile) { "Invalid receipt staging directory." }
        val target = File(context.filesDir, "receipts")
        val previous = File(context.filesDir, "receipts_previous_${UUID.randomUUID()}")
        if (target.exists() && !target.renameTo(previous)) {
            throw IllegalStateException("Could not preserve existing receipt photos.")
        }
        if (!staging.renameTo(target)) {
            previous.renameTo(target)
            throw IllegalStateException("Could not install restored receipt photos.")
        }
        previous.deleteRecursively()
    }

    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Removes files no longer referenced by any transaction. */
    fun prune(referenced: Set<String>) {
        dir.listFiles()?.forEach { if (it.name !in referenced) it.delete() }
    }

    private companion object {
        val RECEIPT_NAME = Regex("""receipt_[0-9a-fA-F-]{36}\.jpg""")
    }
}
