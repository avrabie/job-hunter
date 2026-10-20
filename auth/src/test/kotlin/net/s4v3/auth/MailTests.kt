package net.s4v3.auth

import net.s4v3.auth.mail.AfterCommit
import net.s4v3.auth.mail.LoggingMailSender
import net.s4v3.auth.mail.Mail
import net.s4v3.auth.mail.MailSender
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@IntegrationTest
class MailTests(
    @Autowired private val transactionManager: PlatformTransactionManager,
    @Autowired private val sender: MailSender,
) {
    private val transaction = TransactionTemplate(transactionManager)

    @Test
    fun `without SMTP the default sender is the logging sender`() {
        assertTrue(sender is LoggingMailSender, sender.javaClass.name)
    }

    @Test
    fun `an after-commit action runs after the commit and not before`() {
        val events = mutableListOf<String>()

        transaction.executeWithoutResult {
            AfterCommit.run { events += "sent" }
            events += "in transaction"
        }

        assertEquals(listOf("in transaction", "sent"), events)
    }

    @Test
    fun `an after-commit action does not run when the transaction rolls back`() {
        val events = mutableListOf<String>()

        assertFailsWith<IllegalStateException> {
            transaction.executeWithoutResult {
                AfterCommit.run { events += "sent" }
                error("boom")
            }
        }

        assertEquals(emptyList(), events)
    }

    @Test
    fun `outside a transaction the action runs at once`() {
        val events = mutableListOf<String>()

        AfterCommit.run { events += "sent" }

        assertEquals(listOf("sent"), events)
    }

    @Test
    fun `the logging sender writes the message to the log and nothing else`() {
        val appender = LogCapture.attach()
        try {
            sender.send(Mail("someone@example.com", "Subject line", "Body with https://ui.test/reset-password?token=abc"))
        } finally {
            LogCapture.detach(appender)
        }

        val text = appender.list.joinToString("\n") { it.formattedMessage }
        assertTrue("someone@example.com" in text && "Subject line" in text && "token=abc" in text, text)
    }
}
