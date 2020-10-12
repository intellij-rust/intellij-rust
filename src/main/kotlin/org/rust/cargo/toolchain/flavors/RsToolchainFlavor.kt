/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.flavors

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.io.isDirectory
import org.rust.cargo.toolchain.tools.Cargo
import org.rust.cargo.toolchain.tools.Rustc
import org.rust.cargo.util.hasExecutable
import java.nio.file.Path

abstract class RsToolchainFlavor {

    fun suggestHomePaths(): List<Path> = getHomePathCandidates().distinct().filter { isValidToolchainPath(it) }

    protected abstract fun getHomePathCandidates(): List<Path>

    /**
     * Checks if the path is the name of a Rust toolchain of this flavor.
     *
     * @param path path to check.
     * @return true if paths points to a valid home.
     */
    protected open fun isValidToolchainPath(path: Path): Boolean {
        return path.isDirectory()
            && path.hasExecutable(Rustc.NAME)
            && path.hasExecutable(Cargo.NAME)
    }

    companion object {
        private val EP_NAME: ExtensionPointName<RsToolchainFlavor> = ExtensionPointName.create("org.rust.toolchainFlavor")

        fun getFlavors(): List<RsToolchainFlavor> = EP_NAME.extensionList

        fun getFlavor(path: Path): RsToolchainFlavor? =
            getFlavors().find { flavor -> flavor.isValidToolchainPath(path) }
    }
}
