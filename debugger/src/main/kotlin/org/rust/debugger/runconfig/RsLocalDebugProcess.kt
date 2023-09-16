/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.TextConsoleBuilder
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess

class RsLocalDebugProcess(
    val runParameters: RsDebugRunParameters,
    debugSession: XDebugSession,
    consoleBuilder: TextConsoleBuilder,
) : CidrLocalDebugProcess(runParameters, debugSession, consoleBuilder, { Filter.EMPTY_ARRAY }, runParameters.emulateTerminal) {
    override fun isLibraryFrameFilterSupported() = false
}
