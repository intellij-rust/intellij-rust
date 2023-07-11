/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import java.nio.file.Path

sealed class DebuggerAvailability<out T> {
    object Unavailable : DebuggerAvailability<Nothing>()
    object NeedToDownload : DebuggerAvailability<Nothing>()
    object NeedToUpdate : DebuggerAvailability<Nothing>()
    object Bundled: DebuggerAvailability<Nothing>()
    data class Binaries<T>(val binaries: T) : DebuggerAvailability<T>()
}

data class LLDBBinaries(val frameworkFile: Path, val frontendFile: Path)
data class GDBBinaries(val gdbFile: Path)
