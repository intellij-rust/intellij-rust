/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.openapi.util.registry.Registry
import java.nio.charset.Charset


open class RsProcessHandler : KillableColoredProcessHandler {
    constructor(commandLine: GeneralCommandLine, softKillOnWin: Boolean = SOFT_KILL_ON_WIN) : super(commandLine, softKillOnWin)
    constructor(process: Process, commandLine: String, charset: Charset) : super(process, commandLine, charset)

    override fun shouldDestroyProcessRecursively(): Boolean = true

    companion object {
        @JvmField
        val SOFT_KILL_ON_WIN: Boolean = Registry.`is`("kill.windows.processes.softly", false)
    }
}
