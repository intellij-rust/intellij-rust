/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object RsPathManager {

    fun pluginDir(): Path = plugin().pluginPath
    fun prettyPrintersDir(): Path = pluginDir().resolve("prettyPrinters")

    fun nativeHelper(): Path? {
        val (os, binaryName) = when {
            SystemInfo.isLinux -> "linux" to "intellij-rust-native-helper"
            SystemInfo.isMac -> "macos" to "intellij-rust-native-helper"
            SystemInfo.isWindows -> "windows" to "intellij-rust-native-helper.exe"
            else -> return null
        }
        @Suppress("UnstableApiUsage", "DEPRECATION")
        val arch = when {
            // BACKCOMPAT: 2020.3. Replace with `CpuArch.isIntel64()`
            SystemInfo.isIntel64 -> "x86-64"
            else -> return null
        }

        val nativeHelperPath = pluginDir().resolve("bin/$os/$arch/$binaryName").takeIf { Files.exists(it) } ?: return null
        return if (Files.isExecutable(nativeHelperPath) || nativeHelperPath.toFile().setExecutable(true)) {
            nativeHelperPath
        } else {
            null
        }
    }

    fun pluginDirInSystem(): Path = Paths.get(PathManager.getSystemPath()).resolve("intellij-rust")
    fun tempPluginDirInSystem(): Path = Paths.get(PathManager.getTempPath()).resolve("intellij-rust")
}
