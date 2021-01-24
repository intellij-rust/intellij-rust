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

    fun procMacroExpander(): Path? {
        val (os, binaryName) = when {
            SystemInfo.isLinux -> "linux" to "proc_macro_expander"
            SystemInfo.isMac -> "macos" to "proc_macro_expander"
            SystemInfo.isWindows -> "windows" to "proc_macro_expander.exe"
            else -> return null
        }
        val arch = when {
            // BACKCOMPAT: 2020.2. Replace with `SystemInfo.isIntel64`
            "x86_64" == SystemInfo.OS_ARCH || "amd64" == SystemInfo.OS_ARCH -> "x86-64"
            else -> return null
        }
        // TODO: find out why `pluginDir` returns unexpected dir in tests
        return pluginDir().resolve("bin/$os/$arch/$binaryName").takeIf { Files.exists(it) }
    }

    fun pluginDirInSystem(): Path = Paths.get(PathManager.getSystemPath()).resolve("intellij-rust")
}

