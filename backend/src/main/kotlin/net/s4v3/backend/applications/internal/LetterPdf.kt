// openhtmltopdf 1.1.100 marks useFastMode(), HttpStream and HttpStreamFactory as deprecated and offers no replacement yet
// (same as the CV's PdfRenderer).
@file:Suppress("DEPRECATION")

package net.s4v3.backend.applications.internal

import com.openhtmltopdf.extend.FSSupplier
import com.openhtmltopdf.extend.HttpStream
import com.openhtmltopdf.extend.HttpStreamFactory
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import net.s4v3.backend.applications.LetterPdfBusyException
import net.s4v3.backend.applications.LetterPdfFailedException
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Renders a letter's Markdown to an A4 PDF. The Markdown becomes XHTML with HTML escaped and unsafe links dropped, and
 * goes through the same openhtmltopdf stack and bundled Source Sans 3 fonts as the CV export. Every external resource
 * (images, stylesheets) is refused. At most 2 renders run at once; a third waits a few seconds, then is turned away.
 * Never logs the text.
 */
@Component
internal class LetterPdf {

    private val permits = Semaphore(MAX_PARALLEL)

    fun render(markdown: String): ByteArray {
        if (!permits.tryAcquire(WAIT_SECONDS, TimeUnit.SECONDS)) throw LetterPdfBusyException()
        try {
            val out = ByteArrayOutputStream()
            val builder = PdfRendererBuilder()
            builder.useFastMode()
            builder.useUriResolver { _, _ -> null }
            builder.useHttpStreamImplementation(RefusingHttp)
            for ((file, weight, style) in FONTS) {
                val bytes = FONT_BYTES.getValue(file)
                builder.useFont(FSSupplier<InputStream> { ByteArrayInputStream(bytes) }, FAMILY, weight, style, true)
            }
            builder.withHtmlContent(html(markdown), null)
            builder.toStream(out)
            builder.run()
            return out.toByteArray()
        } catch (e: LetterPdfBusyException) {
            throw e
        } catch (e: Exception) {
            throw LetterPdfFailedException(e)
        } finally {
            permits.release()
        }
    }

    internal fun html(markdown: String): String =
        "<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head>\n<meta charset=\"UTF-8\"/>\n" +
            "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'\"/>\n" +
            "<title>Motivation letter</title>\n<style>$CSS</style>\n</head>\n<body>\n" +
            renderer.render(parser.parse(withSalutation(markdown))) + "</body>\n</html>\n"

    /**
     * The letter prompt leaves "[Salutation]" for the applicant to fill in. When it is still there, the PDF greets the
     * recruiting team instead, in the letter's language (English letters close with "Kind regards").
     */
    internal fun withSalutation(markdown: String): String {
        val english = Regex("kind regards", RegexOption.IGNORE_CASE).containsMatchIn(markdown)
        val greeting = if (english) "Dear Recruiting Team," else "Sehr geehrtes Recruiting-Team,"
        return PLACEHOLDER.replace(markdown, greeting)
    }

    /** Never opens a connection: every request gets an empty stream. */
    private object RefusingHttp : HttpStreamFactory {
        override fun getUrl(url: String?): HttpStream = object : HttpStream {
            override fun getStream(): InputStream = ByteArrayInputStream(ByteArray(0))
            override fun getReader(): Reader = StringReader("")
        }
    }

    private companion object {
        const val MAX_PARALLEL = 2
        const val WAIT_SECONDS = 5L
        const val FAMILY = "Source Sans 3"
        val FONTS = listOf(
            Triple("SourceSans3-Regular.ttf", 400, BaseRendererBuilder.FontStyle.NORMAL),
            Triple("SourceSans3-It.ttf", 400, BaseRendererBuilder.FontStyle.ITALIC),
            Triple("SourceSans3-Bold.ttf", 700, BaseRendererBuilder.FontStyle.NORMAL),
            Triple("SourceSans3-BoldIt.ttf", 700, BaseRendererBuilder.FontStyle.ITALIC),
        )
        // The fonts are the CV export's resources; they are shared on purpose.
        val FONT_BYTES: Map<String, ByteArray> = FONTS.associate { (file, _, _) ->
            file to LetterPdf::class.java.getResourceAsStream("/cv-export/fonts/$file")!!.use { it.readAllBytes() }
        }
        val parser: Parser = Parser.builder().build()
        val PLACEHOLDER = Regex("""\\?\[Salutation\\?\]\s*,?""", RegexOption.IGNORE_CASE)
        // A single newline is a line break, so the closing line and the name stay on separate lines.
        val renderer: HtmlRenderer = HtmlRenderer.builder().softbreak("<br />\n").escapeHtml(true).sanitizeUrls(true).percentEncodeUrls(true).build()

        const val CSS = """
@page { size: A4; margin: 25mm; }
body { font-family: 'Source Sans 3', sans-serif; font-size: 11pt; line-height: 1.45; color: #111; }
p { margin: 0 0 10pt 0; }
h1 { font-size: 16pt; margin: 0 0 10pt 0; }
h2 { font-size: 13pt; margin: 14pt 0 6pt 0; }
h3, h4, h5, h6 { font-size: 11pt; margin: 12pt 0 4pt 0; }
ul, ol { margin: 0 0 10pt 0; padding-left: 18pt; }
li { margin: 0 0 3pt 0; }
blockquote { margin: 0 0 10pt 0; padding-left: 10pt; border-left: 2pt solid #999; color: #333; }
pre, code { font-family: monospace; font-size: 10pt; }
a { color: #111; }
hr { border: 0; border-top: 0.5pt solid #999; margin: 12pt 0; }
"""
    }
}
