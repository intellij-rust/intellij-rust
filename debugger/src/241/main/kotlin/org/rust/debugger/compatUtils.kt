/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration

fun createWslGDBDriverConfiguration(
    wslInfo: DebuggerAvailability.WSL<GDBBinaries>,
    isElevated: Boolean,
    emulateTerminal: Boolean
): DebuggerDriverConfiguration {
    return RsWslGDBDriverConfiguration(wslInfo.distributionId, wslInfo.binaries, isElevated, emulateTerminal)
}
