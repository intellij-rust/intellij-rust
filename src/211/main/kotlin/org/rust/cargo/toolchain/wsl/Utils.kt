/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.wsl

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.util.io.isFile
import java.nio.file.Path

const val WSL_TOOLCHAIN: String = "org.rust.wsl"

fun WSLDistribution.expandUserHome(path: String): String {
    if (!path.startsWith("~/")) return path
    val userHome = userHome ?: return path
    return "$userHome${path.substring(1)}"
}

fun Path.hasExecutableOnWsl(toolName: String): Boolean = pathToExecutableOnWsl(toolName).isFile()

fun Path.pathToExecutableOnWsl(toolName: String): Path = resolve(toolName)
