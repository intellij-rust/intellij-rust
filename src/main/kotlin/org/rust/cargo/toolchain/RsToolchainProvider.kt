/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.openapi.extensions.ExtensionPointName
import java.nio.file.Path

interface RsToolchainProvider {
    fun getToolchain(homePath: Path): RsToolchainBase?

    companion object {
        private val EP_NAME: ExtensionPointName<RsToolchainProvider> =
            ExtensionPointName.create("org.rust.toolchainProvider")

        fun getToolchain(homePath: Path): RsToolchainBase? =
            EP_NAME.extensionList.asSequence()
                .mapNotNull { it.getToolchain(homePath) }
                .firstOrNull()
    }
}
