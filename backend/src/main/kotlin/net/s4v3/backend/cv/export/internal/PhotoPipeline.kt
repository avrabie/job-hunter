package net.s4v3.backend.cv.export.internal

import net.s4v3.backend.cv.export.InvalidPhotoException
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.ImageWriteParam
import javax.imageio.stream.ImageInputStream

/** The photo steps of design decision 4. Every failure is an [InvalidPhotoException] without cause, name or path. */
internal object PhotoPipeline {

    const val MAX_BYTES = 6L * 1024 * 1024
    const val MAX_SIDE = 12_000
    const val MAX_PIXELS = 40_000_000L
    const val SIZE = 320

    fun process(path: Path, declaredSize: Long): ByteArray = try {
        if (declaredSize > MAX_BYTES || declaredSize <= 0) throw InvalidPhotoException()
        val actual = Files.size(path)
        if (actual > MAX_BYTES || actual < 4) throw InvalidPhotoException()
        Files.newInputStream(path).use { raw ->
            ImageIO.createImageInputStream(raw).use { input -> render(input) }
        }
    } catch (e: InvalidPhotoException) {
        throw e
    } catch (e: Exception) {
        throw InvalidPhotoException()
    } catch (e: OutOfMemoryError) {
        throw InvalidPhotoException()
    }

    private fun render(input: ImageInputStream): ByteArray {
        val magic = ByteArray(3)
        input.readFully(magic)
        input.seek(0)
        if (magic[0] != 0xFF.toByte() || magic[1] != 0xD8.toByte() || magic[2] != 0xFF.toByte()) {
            throw InvalidPhotoException()
        }
        val reader = ImageIO.getImageReadersByFormatName("jpeg").asSequence().firstOrNull()
            ?: throw InvalidPhotoException()
        try {
            reader.setInput(input, true, true)
            val width = reader.getWidth(0)
            val height = reader.getHeight(0)
            if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE ||
                width.toLong() * height > MAX_PIXELS
            ) throw InvalidPhotoException()
            // the decoder only warns on a truncated file and returns what it has: a warning means refused
            var warned = false
            reader.addIIOReadWarningListener { _, _ -> warned = true }
            val decoded = try {
                reader.read(0).also { if (warned) throw InvalidPhotoException() }
            } catch (e: InvalidPhotoException) {
                throw e
            } catch (e: Exception) {
                input.seek(0)
                reader.setInput(input, true, true)
                readRasterAsRgb(reader)
            }
            return encode(compose(toRgb(decoded)))
        } finally {
            reader.dispose()
        }
    }

    /** CMYK/YCCK JPEGs have no colour model for `read`; treat the four bands as Adobe (inverted) CMYK. */
    private fun readRasterAsRgb(reader: ImageReader): BufferedImage {
        val raster = reader.readRaster(0, null)
        if (raster.numBands != 4) throw InvalidPhotoException()
        val out = BufferedImage(raster.width, raster.height, BufferedImage.TYPE_INT_RGB)
        val px = IntArray(4)
        for (y in 0 until raster.height) for (x in 0 until raster.width) {
            raster.getPixel(x, y, px)
            val k = px[3]
            out.setRGB(x, y, ((px[0] * k / 255) shl 16) or ((px[1] * k / 255) shl 8) or (px[2] * k / 255))
        }
        return out
    }

    private fun toRgb(src: BufferedImage): BufferedImage {
        if (src.type == BufferedImage.TYPE_INT_RGB) return src
        val rgb = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        val g = rgb.createGraphics()
        try {
            g.color = Color.WHITE
            g.fillRect(0, 0, src.width, src.height)
            g.drawImage(src, 0, 0, null)
        } finally {
            g.dispose()
        }
        return rgb
    }

    private fun compose(src: BufferedImage): BufferedImage {
        val side = minOf(src.width, src.height)
        var current: BufferedImage = src.getSubimage((src.width - side) / 2, (src.height - side) / 2, side, side)
        // halve while at least twice the target, then one final bilinear step
        while (current.width >= SIZE * 2) current = scale(current, (current.width / 2).coerceAtLeast(SIZE))
        current = scale(current, SIZE)
        val out = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        try {
            g.color = Color.WHITE
            g.fillRect(0, 0, SIZE, SIZE)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.clip = Ellipse2D.Double(0.0, 0.0, SIZE.toDouble(), SIZE.toDouble())
            g.drawImage(current, 0, 0, null)
        } finally {
            g.dispose()
        }
        return out
    }

    private fun scale(src: BufferedImage, size: Int): BufferedImage {
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(src, 0, 0, size, size, null)
        } finally {
            g.dispose()
        }
        return out
    }

    private fun encode(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").asSequence().firstOrNull()
            ?: throw InvalidPhotoException()
        try {
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = 0.9f
                progressiveMode = ImageWriteParam.MODE_DISABLED
            }
            val bytes = ByteArrayOutputStream()
            ImageIO.createImageOutputStream(bytes).use { out ->
                writer.output = out
                writer.write(null, IIOImage(image, null, null), param)
            }
            return bytes.toByteArray()
        } finally {
            writer.dispose()
        }
    }
}
