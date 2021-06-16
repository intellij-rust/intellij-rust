/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.wsl

import com.intellij.execution.wsl.WslPath
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.RsToolchainProvider
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.isFeatureEnabled
import java.nio.file.Path

class RsWslToolchainProvider : RsToolchainProvider {
    override fun getToolchain(homePath: Path): RsToolchainBase? {
        if (!isFeatureEnabled(RsExperiments.WSL_TOOLCHAIN)) return null
        val wslPath = WslPath.parseWindowsUncPath(homePath.toString()) ?: return null
        return RsWslToolchain(wslPath)
    }
}
