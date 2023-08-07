/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBBinUrlProvider
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBBinUrlProvider.Bin
import java.net.URL

object RsDebuggerUrlProvider {
    fun lldbFrontend(os: OS, arch: CpuArch): URL? = LLDBBinUrlProvider.lldbFrontend.url(os, arch)
    fun lldb(os: OS, arch: CpuArch): URL? = LLDBBinUrlProvider.lldb.url(os, arch)
    @Suppress("UNUSED_PARAMETER")
    fun gdb(os: OS, arch: CpuArch): URL? = null

    private fun Bin.url(os: OS, arch: CpuArch): URL? {
        return when (os) {
            // Binaries for macos are universal, i.e. they may work on x86 and arm
            OS.macOS -> macX64
            OS.Linux -> {
                when (arch) {
                    CpuArch.X86_64 -> linuxX64
                    CpuArch.ARM64 -> linuxAarch64
                    else -> null
                }
            }
            OS.Windows -> {
                when (arch) {
                    CpuArch.X86_64 -> winX64
                    CpuArch.ARM64 -> winAarch64
                    else -> null
                }
            }
            else -> null
        }
    }
}
