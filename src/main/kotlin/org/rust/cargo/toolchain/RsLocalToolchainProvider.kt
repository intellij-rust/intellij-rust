/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Path

class RsLocalToolchainProvider : RsToolchainProvider {
    override fun getToolchain(homePath: Path): RsToolchainBase? {
        // BACKCOMPAT: 2020.3
        // Use [WslDistributionManager.isWslPath]
        if (SystemInfo.isWindows && homePath.toString().startsWith(WSLDistribution.UNC_PREFIX)) return null
        return RsLocalToolchain(homePath)
    }
}
