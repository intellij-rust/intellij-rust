/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.wsl.WSLUtil
import com.intellij.execution.wsl.WslDistributionManager
import java.nio.file.Path

class RsLocalToolchainProvider : RsToolchainProvider {
    override fun getToolchain(homePath: Path): RsToolchainBase? {
        if (WSLUtil.isSystemCompatible() &&
            (WslDistributionManager.isWslPath(homePath.toString()) ||
                System.getenv("CI_WSL_DISTRO") != null)) return null
        return RsLocalToolchain(homePath)
    }
}
