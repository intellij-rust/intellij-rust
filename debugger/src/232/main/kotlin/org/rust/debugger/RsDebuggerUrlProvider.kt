/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.jetbrains.cidr.execution.debugger.backend.bin.UrlProvider
import java.net.URL

// BACKCOMPAT: 2023.1. Use `UrlProvider` directly
object RsDebuggerUrlProvider {
    fun lldbFrontend(os: OS, arch: CpuArch): URL? = UrlProvider.lldbFrontend(os, arch)
    fun lldb(os: OS, arch: CpuArch): URL? = UrlProvider.lldb(os, arch)
    fun gdb(os: OS, arch: CpuArch): URL? = UrlProvider.gdb(os, arch)
}
