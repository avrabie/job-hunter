package net.s4v3.backend

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.AppenderBase
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Captures the events of [logger] (the root logger in most tests) from the moment it is created until [close]. Thread-safe:
 * async listeners on other threads may still log while a test reads [events].
 */
class LogCapture(private val logger: Logger) : AutoCloseable {
    private val recorded = CopyOnWriteArrayList<ILoggingEvent>()
    private val appender = object : AppenderBase<ILoggingEvent>() {
        override fun append(event: ILoggingEvent) {
            recorded.add(event)
        }
    }

    init {
        appender.context = logger.loggerContext
        appender.start()
        logger.addAppender(appender)
    }

    /** A snapshot of the events captured so far. */
    fun events(): List<ILoggingEvent> = recorded.toList()

    override fun close() {
        logger.detachAppender(appender)
        appender.stop()
    }
}

/** The formatted message, followed by the stack trace when the event carries one. */
fun ILoggingEvent.fullText(): String =
    formattedMessage + "\n" + ((throwableProxy as? ThrowableProxy)?.throwable?.stackTraceToString().orEmpty())
