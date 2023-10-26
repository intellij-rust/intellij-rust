/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import java.io.File

// BACKCOMPAT: 2023.2. Merge with `RsCustomBinariesGDBDriverConfigurationBase`
class RsCustomBinariesGDBDriverConfiguration(
    binaries: GDBBinaries,
    isElevated: Boolean,
    emulateTerminal: Boolean
) : RsCustomBinariesGDBDriverConfigurationBase(binaries, isElevated, emulateTerminal) {
    override fun getWinbreakFile(name: String): File = binaries.gdbDir.resolve("bin/$name").toFile()
}
