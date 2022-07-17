/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Path

class RsLocalToolchainProvider : RsToolchainProvider {
    override fun getToolchain(homePath: Path): RsToolchainBase? {
        if (SystemInfo.isWindows && WslPath.isWslUncPath(homePath.toString())) return null
        return RsLocalToolchain(homePath)
    }
}
