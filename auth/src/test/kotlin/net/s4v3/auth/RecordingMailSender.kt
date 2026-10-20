package net.s4v3.auth

import net.s4v3.auth.mail.LoggingMailSender
import net.s4v3.auth.mail.Mail
import net.s4v3.auth.mail.MailSender
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.CopyOnWriteArrayList

/** Records every mail handed over, and still writes it to the log like the real default sender. */
class RecordingMailSender : MailSender {
    val sent = CopyOnWriteArrayList<Mail>()
    private val logging = LoggingMailSender()

    override fun send(mail: Mail) {
        sent += mail
        logging.send(mail)
    }

    fun to(email: String): List<Mail> = sent.filter { it.to == email }

    /** The reset token inside the link of a reset mail. */
    fun tokenOf(mail: Mail): String = Regex("""token=([A-Za-z0-9_-]+)""").find(mail.body)!!.groupValues[1]
}

@TestConfiguration(proxyBeanMethods = false)
class RecordingMailConfig {
    @Bean
    @Primary
    fun recordingMailSender() = RecordingMailSender()
}
