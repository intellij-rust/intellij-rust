/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.test


import com.intellij.execution.impl.ConsoleBuffer
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.testframework.sm.runner.cutLineIfTooLong
import com.intellij.openapi.util.Key
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

// OutputEventSplitter 2021.3
abstract class CargoTestOutputEventSplitter(
    private val bufferTextUntilNewLine: Boolean = false,
    private val cutNewLineBeforeServiceMessage: Boolean = false
) {
    private val prevRefs: Map<ProcessOutputType, AtomicReference<Output>> =
        listOf(ProcessOutputType.STDOUT, ProcessOutputType.STDERR, ProcessOutputType.SYSTEM).associateWith { AtomicReference<Output>() }

    private val ProcessOutputType.prevRef: AtomicReference<Output>? get() = prevRefs[baseOutputType.baseOutputType]
    private var newLinePending: Boolean = false

    fun process(text: String, outputType: Key<*>) {
        val prevRef = (outputType as? ProcessOutputType)?.prevRef
        if (prevRef == null) {
            flushInternal(text, outputType)
            return
        }

        var mergedText = text
        prevRef.getAndSet(null)?.let {
            if (it.outputType == outputType) {
                mergedText = it.text + text
            } else {
                flushInternal(it.text, it.outputType)
            }
        }
        processInternal(mergedText, outputType)?.let {
            prevRef.set(Output(it, outputType))
        }
    }

    abstract fun onTextAvailable(text: String, outputType: Key<*>)

    private fun processInternal(text: String, outputType: ProcessOutputType): String? {
        var from = 0
        val processServiceMessages = outputType.isStdout
        var newLineInd = text.indexOf(NEW_LINE)
        var teamcityMessageStartInd = if (processServiceMessages) text.indexOf(SERVICE_MESSAGE_START) else -1
        var serviceMessageStarted = false
        while (from < text.length) {
            val nextFrom = min(
                if (newLineInd != -1) newLineInd + 1 else Integer.MAX_VALUE,
                if (teamcityMessageStartInd != -1) teamcityMessageStartInd else Integer.MAX_VALUE
            )
            if (nextFrom == Integer.MAX_VALUE) {
                break
            }
            if (from < nextFrom) {
                flushInternal(text.substring(from, nextFrom), outputType)
            }
            assert(from != nextFrom || from == 0) { "``from`` is $from and it hasn't been changed since last check. Loop is frozen" }
            from = nextFrom
            serviceMessageStarted = processServiceMessages && nextFrom == teamcityMessageStartInd
            if (serviceMessageStarted) {
                teamcityMessageStartInd = text.indexOf(SERVICE_MESSAGE_START, nextFrom + SERVICE_MESSAGE_START.length)
            }
            if (newLineInd != -1 && nextFrom == newLineInd + 1) {
                newLineInd = text.indexOf(NEW_LINE, nextFrom)
            }
        }
        if (from < text.length) {
            val unprocessed = text.substring(from)
            if (serviceMessageStarted) {
                return unprocessed
            }
            val preserveSuffixLength = when {
                bufferTextUntilNewLine -> unprocessed.length
                processServiceMessages -> findSuffixLengthToPreserve(unprocessed)
                else -> 0
            }
            if (preserveSuffixLength < unprocessed.length) {
                flushInternal(unprocessed.substring(0, unprocessed.length - preserveSuffixLength), outputType)
            }
            if (preserveSuffixLength > 0) {
                return unprocessed.substring(unprocessed.length - preserveSuffixLength)
            }
        }
        return null
    }

    private fun findSuffixLengthToPreserve(text: String): Int {
        for (suffixSize in SERVICE_MESSAGE_START.length - 1 downTo 1) {
            if (text.regionMatches(text.length - suffixSize, SERVICE_MESSAGE_START, 0, suffixSize)) {
                return suffixSize
            }
        }
        return 0
    }

    private data class Output(val text: String, val outputType: ProcessOutputType)

    fun flush() {
        prevRefs.values.forEach { reference ->
            reference.getAndSet(null)?.let {
                flushInternal(it.text, it.outputType, lastFlush = true)
            }
        }
    }

    private fun flushInternal(text: String, key: Key<*>, lastFlush: Boolean = false) {
        if (cutNewLineBeforeServiceMessage && key is ProcessOutputType && key.isStdout) {
            if (newLinePending) {
                if (!text.startsWith(SERVICE_MESSAGE_START) || (lastFlush)) {
                    onTextAvailable("\n", key)
                }
                newLinePending = false
            }
            if (text == "\n" && !lastFlush) {
                newLinePending = true
                return
            }
        }

        val textToAdd = if (USE_CYCLE_BUFFER) cutLineIfTooLong(text) else text
        onTextAvailable(textToAdd, key)
    }
}

private val USE_CYCLE_BUFFER: Boolean = ConsoleBuffer.useCycleBuffer()
private const val SERVICE_MESSAGE_START: String = ServiceMessage.SERVICE_MESSAGE_START
private const val NEW_LINE: Char = '\n'
