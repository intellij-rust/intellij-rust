/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.openapi.util.Key

/**
 * Same as [com.intellij.execution.process.KillableColoredProcessHandler], but uses [RsAnsiEscapeDecoder].
 */
class RsKillableColoredProcessHandler(commandLine: GeneralCommandLine)
    : KillableProcessHandler(mediate(commandLine, false, false)), AnsiEscapeDecoder.ColoredTextAcceptor {
    private val ansiEscapeDecoder: AnsiEscapeDecoder = RsAnsiEscapeDecoder()

    init {
        setShouldKillProcessSoftly(true)
    }

    override fun notifyTextAvailable(text: String, outputType: Key<*>) {
        ansiEscapeDecoder.escapeText(text, outputType, this)
    }

    override fun coloredTextAvailable(text: String, attributes: Key<*>) {
        super.notifyTextAvailable(text, attributes)
    }
}
