package net.s4v3.auth

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

/** Collects everything the application logs, for tests that assert something never appears. */
object LogCapture {
    private val root get() = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger

    fun attach(): ListAppender<ILoggingEvent> =
        ListAppender<ILoggingEvent>().also {
            it.start()
            root.addAppender(it)
        }

    fun detach(appender: ListAppender<ILoggingEvent>) {
        root.detachAppender(appender)
        appender.stop()
    }

    /** Each event as "logger: message" plus any exception text. */
    fun lines(appender: ListAppender<ILoggingEvent>): List<String> =
        appender.list.map { event ->
            "${event.loggerName}: ${event.formattedMessage}" +
                (event.throwableProxy?.let { "\n${it.className}: ${it.message}" } ?: "")
        }
}
