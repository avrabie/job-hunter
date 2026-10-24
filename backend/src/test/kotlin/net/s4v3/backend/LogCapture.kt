package net.s4v3.backend

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.AppenderBase
import org.slf4j.LoggerFactory
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

/**
 * Runs [block] with the given [levels] applied (default: [logger] at TRACE) and returns the events [logger] logged while it ran.
 * The appender is detached and every level restored in a finally, also when [block] throws. Several loggers can be set in
 * [levels] (for example the root at INFO and `net.s4v3` at TRACE); the events are still those of [logger].
 */
fun capture(
    logger: Logger = rootLogger(),
    levels: Map<Logger, Level> = mapOf(logger to Level.TRACE),
    block: () -> Unit,
): List<ILoggingEvent> {
    val previous = levels.keys.associateWith { it.level }
    levels.forEach { (target, level) -> target.level = level }
    val log = LogCapture(logger)
    try {
        block()
    } finally {
        log.close()
        previous.forEach { (target, level) -> target.level = level }
    }
    return log.events()
}

/**
 * [capture] for code that runs the application: the root logger stays at INFO, the level the application runs at (Spring
 * prints request URLs and bodies at DEBUG and below, which must not be enabled), and our own `net.s4v3` code is at TRACE.
 */
fun captureAppLogs(block: () -> Unit): List<ILoggingEvent> {
    val root = rootLogger()
    return capture(root, mapOf(root to Level.INFO, LoggerFactory.getLogger("net.s4v3") as Logger to Level.TRACE), block)
}

/** The root logger, for [capture] calls that cover everything the application logs. */
fun rootLogger(): Logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger

/** The formatted message, followed by the stack trace when the event carries one. */
fun ILoggingEvent.fullText(): String =
    formattedMessage + "\n" + ((throwableProxy as? ThrowableProxy)?.throwable?.stackTraceToString().orEmpty())
