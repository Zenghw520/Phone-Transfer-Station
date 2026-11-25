package com.example.transferstation.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** 将用户选择的图片安全地复制并缩放到应用私有图片目录。 */
class ImageStore(context: Context) {
    private val imageDirectory = File(context.filesDir, "chat-images").apply { mkdirs() }
    private val resolver = context.contentResolver

    /** 读取系统照片 URI，限制输入大小后压缩为私有 JPEG 并返回文件路径。 */
    fun importImage(uri: Uri): String {
        val encoded = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_INPUT_BYTES) { "图片过大，请选择小于 20 MB 的图片。" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IllegalArgumentException("无法读取所选图片，请重新选择。")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片尺寸无法读取，请选择常见的 JPEG、PNG 或 WebP 图片。" }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.size, decodeOptions)
            ?: throw IllegalArgumentException("无法识别所选图片格式，请选择常见的 JPEG、PNG 或 WebP 图片。")
        try {
            val scaled = scaleBitmap(decoded)
            try {
                val output = ByteArrayOutputStream()
                check(scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) { "图片压缩失败，请重新选择。" }
                val destination = File(imageDirectory, "${UUID.randomUUID()}.jpg")
                destination.writeBytes(output.toByteArray())
                return destination.absolutePath
            } finally {
                if (scaled !== decoded) scaled.recycle()
            }
        } finally {
            decoded.recycle()
        }
    }

    /** 删除会话引用的私有图片附件。 */
    fun deleteImages(paths: List<String>) {
        paths.forEach { path ->
            val file = File(path)
            if (file.parentFile == imageDirectory) file.delete()
        }
    }

    /** 将超过服务商建议尺寸的图片按比例缩小，避免大图占用过多请求内存。 */
    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val longestSide = maxOf(bitmap.width, bitmap.height)
        if (longestSide <= MAX_IMAGE_EDGE) return bitmap
        val ratio = MAX_IMAGE_EDGE.toFloat() / longestSide
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }

    /** 在解码前按宽高计算采样比，避免高像素压缩图导致设备内存耗尽。 */
    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > MAX_IMAGE_EDGE || height / sampleSize > MAX_IMAGE_EDGE) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private companion object {
        const val MAX_INPUT_BYTES = 20 * 1024 * 1024
        const val MAX_IMAGE_EDGE = 2048
        const val JPEG_QUALITY = 86
    }
}
