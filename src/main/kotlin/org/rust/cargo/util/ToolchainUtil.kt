/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.text.SemVer
import org.rust.stdext.isExecutable
import java.nio.file.Path

fun String.parseSemVer(): SemVer = checkNotNull(SemVer.parseFromText(this)) { "Invalid version value: $this" }

fun Path.hasExecutable(toolName: String): Boolean = pathToExecutable(toolName).isExecutable()

fun Path.pathToExecutable(toolName: String): Path {
    val exeName = if (SystemInfo.isWindows && !WslDistributionManager.isWslPath(this.toString())) "$toolName.exe" else toolName
    return resolve(exeName).toAbsolutePath()
}
