package net.s4v3.backend.cv.export.internal

import com.openhtmltopdf.extend.FSSupplier
import com.openhtmltopdf.extend.HttpStream
import com.openhtmltopdf.extend.HttpStreamFactory
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import net.s4v3.backend.cv.export.CvDocument
import net.s4v3.backend.cv.export.CvRenderer
import net.s4v3.backend.cv.export.CvTemplate
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.Reader
import java.io.StringReader

/** Renders the [HtmlRenderer] output to an A4 PDF with the bundled fonts. Refuses every external resource. */
internal class PdfRenderer(private val html: HtmlRenderer = HtmlRenderer()) : CvRenderer {

    override fun render(document: CvDocument, template: CvTemplate): ByteArray {
        val out = ByteArrayOutputStream()
        val builder = PdfRendererBuilder()
        builder.useFastMode()
        // No URI is ever resolved, so nothing is fetched (files, http, https); the http factory is a second guard.
        builder.useUriResolver { _, _ -> null }
        builder.useHttpStreamImplementation(RefusingHttp)
        for ((file, weight, style) in FONTS) {
            val bytes = FONT_BYTES.getValue(file)
            builder.useFont(FSSupplier<InputStream> { ByteArrayInputStream(bytes) }, FAMILY, weight, style, true)
        }
        builder.withHtmlContent(html.html(document, template), null)
        builder.toStream(out)
        builder.run()
        return out.toByteArray()
    }

    /** Never opens a connection: every request gets an empty stream. */
    private object RefusingHttp : HttpStreamFactory {
        override fun getUrl(url: String?): HttpStream = object : HttpStream {
            override fun getStream(): InputStream = ByteArrayInputStream(ByteArray(0))
            override fun getReader(): Reader = StringReader("")
        }
    }

    private companion object {
        const val FAMILY = "Source Sans 3"
        val FONTS = listOf(
            Triple("SourceSans3-Regular.ttf", 400, BaseRendererBuilder.FontStyle.NORMAL),
            Triple("SourceSans3-It.ttf", 400, BaseRendererBuilder.FontStyle.ITALIC),
            Triple("SourceSans3-Bold.ttf", 700, BaseRendererBuilder.FontStyle.NORMAL),
            Triple("SourceSans3-BoldIt.ttf", 700, BaseRendererBuilder.FontStyle.ITALIC),
        )
        val FONT_BYTES: Map<String, ByteArray> = FONTS.associate { (file, _, _) ->
            file to PdfRenderer::class.java.getResourceAsStream("/cv-export/fonts/$file")!!.use { it.readAllBytes() }
        }
    }
}
