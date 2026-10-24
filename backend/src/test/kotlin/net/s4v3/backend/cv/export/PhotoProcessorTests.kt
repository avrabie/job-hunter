package net.s4v3.backend.cv.export

import net.s4v3.backend.files.CabinetFile
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.awt.image.DataBuffer
import java.awt.image.Raster
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PhotoProcessorTests {

    private val processor = PhotoProcessor()
    private val dir: Path = Files.createTempDirectory("photo-tests")

    private fun file(bytes: ByteArray, size: Long = bytes.size.toLong()): CabinetFile {
        val p = Files.createTempFile(dir, "p", ".bin")
        Files.write(p, bytes)
        return CabinetFile(UUID.randomUUID(), "x.jpg", "image/jpeg", size, p)
    }

    private fun drawn(w: Int, h: Int, type: Int = BufferedImage.TYPE_INT_RGB): BufferedImage {
        val img = BufferedImage(w, h, type)
        val g = img.createGraphics()
        g.paint = GradientPaint(0f, 0f, Color(200, 30, 30), w.toFloat(), h.toFloat(), Color(180, 20, 60))
        g.fillRect(0, 0, w, h)
        g.dispose()
        return img
    }

    private fun jpeg(img: BufferedImage, exif: Boolean = false): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { ios ->
            writer.output = ios
            var meta: javax.imageio.metadata.IIOMetadata? = null
            if (exif) {
                meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(img), null)
                val root = meta.getAsTree("javax_imageio_jpeg_image_1.0")
                val seq = root.lastChild
                val unknown = IIOMetadataNode("unknown")
                unknown.setAttribute("MarkerTag", "225")
                unknown.userObject = "Exif\u0000\u0000synthetic-gps".toByteArray()
                seq.appendChild(unknown)
                meta.setFromTree("javax_imageio_jpeg_image_1.0", root)
            }
            writer.write(null, IIOImage(img, null, meta), writer.defaultWriteParam)
        }
        writer.dispose()
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(bytes))

    private fun markers(b: ByteArray): List<Int> {
        val found = mutableListOf<Int>()
        var i = 2
        while (i + 3 < b.size && (b[i].toInt() and 0xFF) == 0xFF) {
            val m = b[i + 1].toInt() and 0xFF
            found += m
            if (m == 0xDA) break
            i += 2 + (((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF))
        }
        return found
    }

    /** Overwrites the height and width of the first start-of-frame segment. */
    private fun withDimensions(src: ByteArray, w: Int, h: Int): ByteArray {
        val sof = (2 until src.size - 8).first {
            src[it] == 0xFF.toByte() && (src[it + 1] == 0xC0.toByte() || src[it + 1] == 0xC2.toByte())
        }
        val p = src.copyOf()
        p[sof + 5] = (h shr 8).toByte(); p[sof + 6] = (h and 0xFF).toByte()
        p[sof + 7] = (w shr 8).toByte(); p[sof + 8] = (w and 0xFF).toByte()
        return p
    }

    @Test
    fun `wide jpeg becomes a 320 square jpeg with white corners and a coloured centre`() {
        val out = processor.process(file(jpeg(drawn(1000, 600))))
        assertEquals(0xFF, out[0].toInt() and 0xFF)
        assertEquals(0xD8, out[1].toInt() and 0xFF)
        val img = decode(out)
        assertEquals(320, img.width)
        assertEquals(320, img.height)
        for ((x, y) in listOf(0 to 0, 319 to 0, 0 to 319, 319 to 319)) {
            val c = Color(img.getRGB(x, y))
            assertTrue(c.red > 240 && c.green > 240 && c.blue > 240, "corner $x,$y")
        }
        val centre = Color(img.getRGB(160, 160))
        assertTrue(centre.red > 150 && centre.green < 90, "centre keeps colour: $centre")
    }

    @Test
    fun `same input gives same bytes`() {
        val src = jpeg(drawn(800, 800))
        assertContentEquals(processor.process(file(src)), processor.process(file(src)))
    }

    @Test
    fun `output has no APP1 or comment segment`() {
        val src = jpeg(drawn(500, 500), exif = true)
        assertTrue(markers(src).contains(0xE1), "test input must carry APP1")
        val out = markers(processor.process(file(src)))
        assertTrue(out.none { it == 0xFE || it in 0xE1..0xEF }, "markers: $out")
    }

    @Test
    fun `greyscale jpeg converts`() {
        val out = processor.process(file(jpeg(drawn(400, 300, BufferedImage.TYPE_BYTE_GRAY))))
        assertEquals(320, decode(out).width)
    }

    @Test
    fun `cmyk jpeg converts`() {
        val raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, 64, 64, 4, null)
        val px = intArrayOf(255, 40, 40, 255)
        for (y in 0 until 64) for (x in 0 until 64) raster.setPixel(x, y, px)
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { ios ->
            writer.output = ios
            writer.write(null, IIOImage(raster, null, null), writer.defaultWriteParam)
        }
        writer.dispose()
        assertEquals(320, decode(processor.process(file(out.toByteArray()))).width)
    }

    @Test
    fun `truncated file is refused`() {
        val src = jpeg(drawn(600, 600))
        assertFailsWith<InvalidPhotoException> { processor.process(file(src.copyOf(src.size / 3))) }
    }

    @Test
    fun `png is refused`() {
        val out = ByteArrayOutputStream()
        ImageIO.write(drawn(100, 100), "png", out)
        assertFailsWith<InvalidPhotoException> { processor.process(file(out.toByteArray())) }
    }

    @Test
    fun `thirteen thousand pixel wide header only jpeg is refused`() {
        val patched = withDimensions(jpeg(drawn(64, 64)), 13_000, 100)
        assertFailsWith<InvalidPhotoException> { processor.process(file(patched)) }
    }

    @Test
    fun `more than forty million pixels is refused`() {
        val patched = withDimensions(jpeg(drawn(64, 64)), 11_000, 11_000)
        assertFailsWith<InvalidPhotoException> { processor.process(file(patched)) }
    }

    @Test
    fun `seven megabyte file is refused by size`() {
        val big = ByteArray(7 * 1024 * 1024)
        big[0] = 0xFF.toByte(); big[1] = 0xD8.toByte(); big[2] = 0xFF.toByte()
        assertFailsWith<InvalidPhotoException> { processor.process(file(big)) }
        assertFailsWith<InvalidPhotoException> { processor.process(file(big, size = 1000)) }
    }

    @Test
    fun `exception message carries no name or path`() {
        val f = file(byteArrayOf(1, 2, 3, 4, 5))
        val e = assertFailsWith<InvalidPhotoException> { processor.process(f) }
        assertTrue(!e.message!!.contains(f.path.toString()) && !e.message!!.contains(f.name))
    }
}
