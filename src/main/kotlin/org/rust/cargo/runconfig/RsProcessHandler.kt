/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.openapi.util.Key
import com.intellij.util.io.BaseDataReader
import com.intellij.util.io.BaseOutputReader
import com.pty4j.PtyProcess
import java.nio.charset.Charset

/**
 * Same as [com.intellij.execution.process.KillableColoredProcessHandler], but uses [RsAnsiEscapeDecoder].
 */
class RsProcessHandler : KillableProcessHandler, AnsiEscapeDecoder.ColoredTextAcceptor {
    private val decoder: AnsiEscapeDecoder?

    init {
        setShouldDestroyProcessRecursively(!hasPty())
    }

    constructor(
        commandLine: GeneralCommandLine,
        processColors: Boolean = commandLine !is PtyCommandLine
    ) : super(commandLine) {
        decoder = if (processColors) RsAnsiEscapeDecoder() else null
    }

    constructor(
        process: Process,
        commandRepresentation: String,
        charset: Charset,
        processColors: Boolean = process !is PtyProcess
    ) : super(process, commandRepresentation, charset) {
        decoder = if (processColors) RsAnsiEscapeDecoder() else null
    }

    override fun notifyTextAvailable(text: String, outputType: Key<*>) {
        decoder?.escapeText(text, outputType, this) ?: super.notifyTextAvailable(text, outputType)
    }

    override fun coloredTextAvailable(text: String, attributes: Key<*>) {
        super.notifyTextAvailable(text, attributes)
    }

    override fun readerOptions(): BaseOutputReader.Options = object : BaseOutputReader.Options() {
        override fun policy(): BaseDataReader.SleepingPolicy =
            if (hasPty() || java.lang.Boolean.getBoolean("output.reader.blocking.mode")) {
                BaseDataReader.SleepingPolicy.BLOCKING
            } else {
                BaseDataReader.SleepingPolicy.NON_BLOCKING
            }

        override fun splitToLines(): Boolean = !hasPty()
    }
}
