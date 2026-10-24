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
import javax.imageio.ImageReadParam
import javax.imageio.ImageReader
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import javax.imageio.stream.ImageInputStream

/** The photo steps of design decision 4. Every failure is an [InvalidPhotoException] without cause, name or path. */
internal object PhotoPipeline {

    const val MAX_BYTES = 6L * 1024 * 1024
    const val MAX_SIDE = 12_000
    const val MAX_PIXELS = 16_000_000L
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
        val orientation = readOrientation(input)
        input.seek(0)
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
            // Decode already reduced: the short side ends between 2x and 4x the target (integer step, deterministic).
            val step = (minOf(width, height) / (SIZE * 2)).coerceAtLeast(1)
            val param = reader.defaultReadParam
            param.setSourceSubsampling(step, step, 0, 0)
            val rgbType = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB)
            val types = try {
                reader.getImageTypes(0).asSequence().toList()
            } catch (_: Exception) {
                emptyList() // no colour model (CMYK/YCCK): the raster path below checks the band count
            }
            val decoded = if (types.isEmpty()) {
                readRasterAsRgb(reader, param)
            } else {
                if (types.any { it.bufferedImageType == BufferedImage.TYPE_INT_RGB }) param.destinationType = rgbType
                reader.read(0, param)
            }
            if (warned) throw InvalidPhotoException()
            return encode(compose(toRgb(decoded), orientation))
        } finally {
            reader.dispose()
        }
    }

    /** CMYK/YCCK JPEGs have no colour model for `read`; treat the four bands as Adobe (inverted) CMYK. Input is subsampled. */
    private fun readRasterAsRgb(reader: ImageReader, param: ImageReadParam): BufferedImage {
        val raster = reader.readRaster(0, param)
        if (raster.numBands != 4) throw InvalidPhotoException()
        val out = BufferedImage(raster.width, raster.height, BufferedImage.TYPE_INT_RGB)
        val px = IntArray(4)
        for (y in 0 until raster.height) for (x in 0 until raster.width) {
            raster.getPixel(raster.minX + x, raster.minY + y, px)
            val k = px[3]
            out.setRGB(x, y, ((px[0] * k / 255) shl 16) or ((px[1] * k / 255) shl 8) or (px[2] * k / 255))
        }
        return out
    }

    /**
     * The EXIF orientation (1..8) from the first APP1 "Exif" segment, 1 when absent or malformed. Reads only marker
     * segments up to the start of scan, each bounded by its 16-bit length; leaves the stream position undefined.
     */
    private fun readOrientation(input: ImageInputStream): Int {
        try {
            input.seek(2)
            while (true) {
                if (input.read() != 0xFF) return 1
                var marker = input.read()
                while (marker == 0xFF) marker = input.read()
                if (marker < 0 || marker == 0xDA || marker == 0xD9) return 1
                if (marker == 0x01 || marker in 0xD0..0xD8) continue
                val len = input.readUnsignedShort() - 2
                if (len < 0) return 1
                if (marker == 0xE1 && len >= 14 + 6) {
                    val seg = ByteArray(len)
                    input.readFully(seg)
                    if (seg[0] == 'E'.code.toByte() && seg[1] == 'x'.code.toByte() && seg[2] == 'i'.code.toByte() &&
                        seg[3] == 'f'.code.toByte() && seg[4] == 0.toByte() && seg[5] == 0.toByte()
                    ) return parseTiffOrientation(seg, 6)
                } else {
                    input.skipBytes(len)
                }
            }
        } catch (_: Exception) {
            return 1
        }
    }

    private fun parseTiffOrientation(b: ByteArray, base: Int): Int {
        fun u8(i: Int) = b[i].toInt() and 0xFF
        val little = when {
            u8(base) == 'I'.code && u8(base + 1) == 'I'.code -> true
            u8(base) == 'M'.code && u8(base + 1) == 'M'.code -> false
            else -> return 1
        }
        fun u16(i: Int): Int = if (little) u8(i) or (u8(i + 1) shl 8) else (u8(i) shl 8) or u8(i + 1)
        fun u32(i: Int): Long =
            if (little) (u16(i).toLong() or (u16(i + 2).toLong() shl 16)) else ((u16(i).toLong() shl 16) or u16(i + 2).toLong())
        if (base + 8 > b.size || u16(base + 2) != 42) return 1
        val ifd = u32(base + 4)
        if (ifd < 8 || ifd > b.size) return 1
        val at = base + ifd.toInt()
        if (at + 2 > b.size) return 1
        val count = u16(at)
        for (i in 0 until count) {
            val e = at + 2 + i * 12
            if (e + 12 > b.size) return 1
            if (u16(e) == 0x0112 && u16(e + 2) == 3 && u32(e + 4) == 1L) {
                val v = u16(e + 8)
                return if (v in 1..8) v else 1
            }
        }
        return 1
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

    private fun compose(src: BufferedImage, orientation: Int): BufferedImage {
        val side = minOf(src.width, src.height)
        // cropping the centre square first, then turning it, equals turning the image and cropping; mirrored values count as 1
        var current: BufferedImage = rotate(
            src.getSubimage((src.width - side) / 2, (src.height - side) / 2, side, side), orientation,
        )
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

    /** Turns a square so it shows upright: 3 = 180, 6 = 90 clockwise, 8 = 90 counter-clockwise; anything else unchanged. */
    private fun rotate(src: BufferedImage, orientation: Int): BufferedImage {
        if (orientation != 3 && orientation != 6 && orientation != 8) return src
        val n = src.width
        val from = src.getRGB(0, 0, n, n, null, 0, n)
        val to = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val v = from[y * n + x]
            when (orientation) {
                3 -> to[(n - 1 - y) * n + (n - 1 - x)] = v
                6 -> to[x * n + (n - 1 - y)] = v
                else -> to[(n - 1 - x) * n + y] = v
            }
        }
        val out = BufferedImage(n, n, BufferedImage.TYPE_INT_RGB)
        out.setRGB(0, 0, n, n, to, 0, n)
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
