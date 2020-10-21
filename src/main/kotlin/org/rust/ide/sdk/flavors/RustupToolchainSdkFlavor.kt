/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.flavors

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import org.rust.stdext.list
import org.rust.stdext.toPath
import java.nio.file.Path

// TODO: remove before merge
object RustupToolchainSdkFlavor : RsSdkFlavor {
    override fun isApplicable(): Boolean = SystemInfo.isUnix

    override fun getHomePathCandidates(): List<Path> {
        val toolchains = FileUtil.expandUserHome("~/.rustup/toolchains").toPath()
        if (!toolchains.exists() || !toolchains.isDirectory()) return emptyList()
        return toolchains.list()
            .filter { file -> file.isDirectory() }
            .map { it.resolve("bin") }
            .filter { it.isDirectory() }
            .toList()
    }
}
