package net.s4v3.auth.mail

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

data class Mail(val to: String, val subject: String, val body: String)

/** The one way the service sends email. An SMTP implementation can replace the logging one by defining a bean. */
interface MailSender {
    fun send(mail: Mail)
}

/**
 * Stand-in until SMTP exists: writes the message to the log, so for a password reset the log line holds the reset
 * link (and its token). Acceptable only because the sender is temporary and tokens are single-use and short-lived.
 */
class LoggingMailSender : MailSender {
    override fun send(mail: Mail) {
        log.info("Mail (not sent, logging sender) to={} subject={}\n{}", mail.to, mail.subject, mail.body)
    }

    private companion object {
        val log = LoggerFactory.getLogger(LoggingMailSender::class.java)
    }
}

@Configuration(proxyBeanMethods = false)
class MailConfig {
    @Bean
    @ConditionalOnMissingBean(MailSender::class)
    fun loggingMailSender(): MailSender = LoggingMailSender()
}

/** Runs [action] after the current transaction commits (never on rollback), or at once when there is none. */
object AfterCommit {
    fun run(action: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = action()
        })
    }
}
