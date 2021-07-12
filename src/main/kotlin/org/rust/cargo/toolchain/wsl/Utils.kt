/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.wsl

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import java.nio.file.Path

fun WSLDistribution.expandUserHome(path: String): String {
    if (!path.startsWith("~/")) return path
    val userHome = userHome ?: return path
    return "$userHome${path.substring(1)}"
}

fun WslDistributionManager.computeInstalledDistributions(): List<WSLDistribution> {
    // TODO: CI's WSL doesn't have `--list` flag
    System.getenv("CI_WSL_DISTRO")?.let { return listOf(WSLDistribution(it)) }
    return compute("Getting installed distributions...") { installedDistributions }
}

fun Path.hasExecutableOnWsl(toolName: String): Boolean = pathToExecutableOnWsl(toolName).toFile().isFile

fun Path.pathToExecutableOnWsl(toolName: String): Path = resolve(toolName)
