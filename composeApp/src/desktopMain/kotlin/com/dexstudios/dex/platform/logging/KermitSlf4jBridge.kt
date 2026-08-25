package com.dexstudios.dex.platform.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.Marker
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.helpers.MessageFormatter
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider
import java.util.ArrayDeque
import java.util.Deque
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridges every SLF4J-based library (Ktor server/client, jmDNS, ...) into the project-wide
 * Kermit pipeline so the app emits exactly one log stream. Registered with SLF4J through
 * META-INF/services/org.slf4j.spi.SLF4JServiceProvider.
 */
class KermitSlf4jServiceProvider : SLF4JServiceProvider {

    private val loggerFactory = KermitSlf4jLoggerFactory()
    private val markerFactory = BasicMarkerFactory()
    private val mdcAdapter = KermitMDCAdapter()

    override fun getLoggerFactory(): ILoggerFactory = loggerFactory
    override fun getMarkerFactory(): IMarkerFactory = markerFactory
    override fun getMDCAdapter(): MDCAdapter = mdcAdapter
    override fun getRequestedApiVersion(): String = "2.0.99"
    override fun initialize() = Unit
}

internal class KermitSlf4jLoggerFactory : ILoggerFactory {
    private val loggers = ConcurrentHashMap<String, org.slf4j.Logger>()

    override fun getLogger(name: String): org.slf4j.Logger = loggers.computeIfAbsent(name, ::KermitSlf4jLogger)
}

internal class KermitMDCAdapter : MDCAdapter {
    private val context = ThreadLocal.withInitial { HashMap<String, String>() }
    private val stacks = ConcurrentHashMap<String, ArrayDeque<String>>()

    override fun put(key: String, value: String) {
        context.get()[key] = value
    }

    override fun get(key: String): String? = context.get()[key]

    override fun remove(key: String) {
        context.get().remove(key)
    }

    override fun clear() {
        context.get().clear()
    }

    override fun getCopyOfContextMap(): MutableMap<String, String>? = HashMap(context.get())

    override fun setContextMap(contextMap: MutableMap<String, String>) {
        val map = context.get()
        map.clear()
        map.putAll(contextMap)
    }

    override fun pushByKey(key: String?, value: String?) {
        if (key == null) return
        stacks.computeIfAbsent(key) { ArrayDeque() }.push(value ?: return)
    }

    override fun popByKey(key: String?): String? = stacks[key]?.pollFirst()

    override fun getCopyOfDequeByKey(key: String?): Deque<String>? = stacks[key]?.let { ArrayDeque(it) }

    override fun clearDequeByKey(key: String?) {
        if (key != null) stacks.remove(key)
    }
}

internal class KermitSlf4jLogger(private val name: String) : org.slf4j.Logger {

    private val tag = name.substringAfterLast('.').ifEmpty { name }

    override fun getName(): String = name

    private fun emit(severity: Severity, format: String?, args: Array<out Any?>, marker: Marker?) {
        val tuple = MessageFormatter.arrayFormat(format, args)
        logToKermit(severity, tuple.message, tuple.throwable, marker)
    }

    private fun emit(severity: Severity, msg: String?, t: Throwable?, marker: Marker?) {
        logToKermit(severity, msg, t, marker)
    }

    private fun logToKermit(severity: Severity, message: String?, throwable: Throwable?, marker: Marker?) {
        val markerPrefix = if (marker != null && marker.hasReferences()) "[${marker.name}] " else ""
        Logger.log(severity, tag, throwable, markerPrefix + message.orEmpty())
    }

    // SLF4J level gates delegate to Kermit's own severity filtering at write time.
    private fun enabled(): Boolean = true

    override fun isTraceEnabled(): Boolean = enabled()
    override fun isTraceEnabled(marker: Marker?): Boolean = enabled()
    override fun isDebugEnabled(): Boolean = enabled()
    override fun isDebugEnabled(marker: Marker?): Boolean = enabled()
    override fun isInfoEnabled(): Boolean = enabled()
    override fun isInfoEnabled(marker: Marker?): Boolean = enabled()
    override fun isWarnEnabled(): Boolean = enabled()
    override fun isWarnEnabled(marker: Marker?): Boolean = enabled()
    override fun isErrorEnabled(): Boolean = enabled()
    override fun isErrorEnabled(marker: Marker?): Boolean = enabled()

    override fun trace(msg: String?) = emit(Severity.Verbose, msg, null as Throwable?, null)
    override fun trace(format: String?, arg: Any?) = emit(Severity.Verbose, format, arrayOf(arg), null)
    override fun trace(format: String?, arg1: Any?, arg2: Any?) = emit(Severity.Verbose, format, arrayOf(arg1, arg2), null)
    override fun trace(format: String?, vararg arguments: Any?) = emit(Severity.Verbose, format, arguments, null)
    override fun trace(msg: String?, t: Throwable?) = emit(Severity.Verbose, msg, t, null)
    override fun trace(marker: Marker?, msg: String?) = emit(Severity.Verbose, msg, null as Throwable?, marker)
    override fun trace(marker: Marker?, format: String?, arg: Any?) = emit(Severity.Verbose, format, arrayOf(arg), marker)
    override fun trace(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) = emit(Severity.Verbose, format, arrayOf(arg1, arg2), marker)
    override fun trace(marker: Marker?, format: String?, vararg arguments: Any?) = emit(Severity.Verbose, format, arguments, marker)
    override fun trace(marker: Marker?, msg: String?, t: Throwable?) = emit(Severity.Verbose, msg, t, marker)

    override fun debug(msg: String?) = emit(Severity.Debug, msg, null as Throwable?, null)
    override fun debug(format: String?, arg: Any?) = emit(Severity.Debug, format, arrayOf(arg), null)
    override fun debug(format: String?, arg1: Any?, arg2: Any?) = emit(Severity.Debug, format, arrayOf(arg1, arg2), null)
    override fun debug(format: String?, vararg arguments: Any?) = emit(Severity.Debug, format, arguments, null)
    override fun debug(msg: String?, t: Throwable?) = emit(Severity.Debug, msg, t, null)
    override fun debug(marker: Marker?, msg: String?) = emit(Severity.Debug, msg, null as Throwable?, marker)
    override fun debug(marker: Marker?, format: String?, arg: Any?) = emit(Severity.Debug, format, arrayOf(arg), marker)
    override fun debug(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) = emit(Severity.Debug, format, arrayOf(arg1, arg2), marker)
    override fun debug(marker: Marker?, format: String?, vararg arguments: Any?) = emit(Severity.Debug, format, arguments, marker)
    override fun debug(marker: Marker?, msg: String?, t: Throwable?) = emit(Severity.Debug, msg, t, marker)

    override fun info(msg: String?) = emit(Severity.Info, msg, null as Throwable?, null)
    override fun info(format: String?, arg: Any?) = emit(Severity.Info, format, arrayOf(arg), null)
    override fun info(format: String?, arg1: Any?, arg2: Any?) = emit(Severity.Info, format, arrayOf(arg1, arg2), null)
    override fun info(format: String?, vararg arguments: Any?) = emit(Severity.Info, format, arguments, null)
    override fun info(msg: String?, t: Throwable?) = emit(Severity.Info, msg, t, null)
    override fun info(marker: Marker?, msg: String?) = emit(Severity.Info, msg, null as Throwable?, marker)
    override fun info(marker: Marker?, format: String?, arg: Any?) = emit(Severity.Info, format, arrayOf(arg), marker)
    override fun info(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) = emit(Severity.Info, format, arrayOf(arg1, arg2), marker)
    override fun info(marker: Marker?, format: String?, vararg arguments: Any?) = emit(Severity.Info, format, arguments, marker)
    override fun info(marker: Marker?, msg: String?, t: Throwable?) = emit(Severity.Info, msg, t, marker)

    override fun warn(msg: String?) = emit(Severity.Warn, msg, null as Throwable?, null)
    override fun warn(format: String?, arg: Any?) = emit(Severity.Warn, format, arrayOf(arg), null)
    override fun warn(format: String?, arg1: Any?, arg2: Any?) = emit(Severity.Warn, format, arrayOf(arg1, arg2), null)
    override fun warn(format: String?, vararg arguments: Any?) = emit(Severity.Warn, format, arguments, null)
    override fun warn(msg: String?, t: Throwable?) = emit(Severity.Warn, msg, t, null)
    override fun warn(marker: Marker?, msg: String?) = emit(Severity.Warn, msg, null as Throwable?, marker)
    override fun warn(marker: Marker?, format: String?, arg: Any?) = emit(Severity.Warn, format, arrayOf(arg), marker)
    override fun warn(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) = emit(Severity.Warn, format, arrayOf(arg1, arg2), marker)
    override fun warn(marker: Marker?, format: String?, vararg arguments: Any?) = emit(Severity.Warn, format, arguments, marker)
    override fun warn(marker: Marker?, msg: String?, t: Throwable?) = emit(Severity.Warn, msg, t, marker)

    override fun error(msg: String?) = emit(Severity.Error, msg, null as Throwable?, null)
    override fun error(format: String?, arg: Any?) = emit(Severity.Error, format, arrayOf(arg), null)
    override fun error(format: String?, arg1: Any?, arg2: Any?) = emit(Severity.Error, format, arrayOf(arg1, arg2), null)
    override fun error(format: String?, vararg arguments: Any?) = emit(Severity.Error, format, arguments, null)
    override fun error(msg: String?, t: Throwable?) = emit(Severity.Error, msg, t, null)
    override fun error(marker: Marker?, msg: String?) = emit(Severity.Error, msg, null as Throwable?, marker)
    override fun error(marker: Marker?, format: String?, arg: Any?) = emit(Severity.Error, format, arrayOf(arg), marker)
    override fun error(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) = emit(Severity.Error, format, arrayOf(arg1, arg2), marker)
    override fun error(marker: Marker?, format: String?, vararg arguments: Any?) = emit(Severity.Error, format, arguments, marker)
    override fun error(marker: Marker?, msg: String?, t: Throwable?) = emit(Severity.Error, msg, t, marker)
}
