/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.wsl

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import java.nio.file.Path

fun WSLDistribution.expandUserHome(path: String): String {
    if (!path.startsWith("~/")) return path
    val userHome = userHome ?: return path
    return "$userHome${path.substring(1)}"
}

fun Path.hasExecutableOnWsl(toolName: String): Boolean {
    val wslExecutable = pathToExecutableOnWsl(toolName)
    return wslExecutable.toFile().isFile || wslExecutable.isWslExecutable()
}

fun Path.pathToExecutableOnWsl(toolName: String): Path = resolve(toolName)

fun Path.isWslExecutable(): Boolean {
    val wslPath = WslPath.parseWindowsUncPath(this.toString())?: return false
    val output = wslPath.distribution.executeOnWsl(1000, "test", "-x", wslPath.linuxPath)
    return output.exitCode == 0
}
