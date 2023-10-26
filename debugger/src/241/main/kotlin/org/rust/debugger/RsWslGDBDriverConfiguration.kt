/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import com.jetbrains.cidr.system.HostMachine
import com.jetbrains.cidr.toolchains.wsl.CidrWSLDistribution
import com.jetbrains.cidr.toolchains.wsl.CidrWSLHost

class RsWslGDBDriverConfiguration(
    wslDistributionId: String,
    binaries: GDBBinaries,
    isElevated: Boolean,
    emulateTerminal: Boolean
) : RsCustomBinariesGDBDriverConfigurationBase(binaries, isElevated, emulateTerminal) {

    private val wslHost: CidrWSLHost = CidrWSLHost(CidrWSLDistribution(wslDistributionId))

    override fun getDriverName(): String = "GDB"
    override fun getHostMachine(): HostMachine = wslHost

    override fun convertToEnvPath(localPath: String?): String? {
        if (localPath == null) return null
        return wslHost.convertToRemote(localPath)
    }

    override fun convertToLocalPath(absolutePath: String?): String? {
        if (absolutePath == null) return null
        return wslHost.convertToLocal(absolutePath)
    }
}
