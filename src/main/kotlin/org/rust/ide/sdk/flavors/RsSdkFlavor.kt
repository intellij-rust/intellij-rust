/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.flavors

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.isDirectory
import org.rust.cargo.toolchain.tools.Cargo
import org.rust.cargo.toolchain.tools.Rustc
import org.rust.stdext.isExecutable
import org.rust.stdext.toPath
import java.nio.file.Path

fun RsSdkFlavor.suggestHomePaths(): List<Path> =
    getHomePathCandidates().distinct().filter { isValidSdkPath(it) }

interface RsSdkFlavor {

    /**
     * Flavor is added to result in [getApplicableFlavors] if this method returns true.
     * @return whether this flavor is applicable.
     */
    fun isApplicable(): Boolean = true

    fun getHomePathCandidates(): List<Path>

    /**
     * Checks if the path is the name of a Rust toolchain of this flavor.
     *
     * @param sdkPath path to check.
     * @return true if paths points to a valid home.
     */
    fun isValidSdkPath(sdkPath: Path): Boolean {
        return sdkPath.isDirectory()
            && sdkPath.hasExecutable(Rustc.NAME)
            && sdkPath.hasExecutable(Cargo.NAME)
    }

    companion object {
        @JvmField
        val EP_NAME: ExtensionPointName<RsSdkFlavor> = ExtensionPointName.create("org.rust.sdkFlavor")

        fun getApplicableFlavors(): List<RsSdkFlavor> = EP_NAME.extensionList.filter { it.isApplicable() }

        fun getFlavor(sdk: Sdk): RsSdkFlavor? = getFlavor(sdk.homePath?.toPath())

        fun getFlavor(sdkPath: Path?): RsSdkFlavor? {
            if (sdkPath == null) return null
            return getApplicableFlavors().find { flavor -> flavor.isValidSdkPath(sdkPath) }
        }

        // TODO: Move?
        @JvmStatic
        fun Path.hasExecutable(toolName: String): Boolean = pathToExecutable(toolName).isExecutable()

        private fun Path.pathToExecutable(toolName: String): Path {
            val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
            return resolve(exeName).toAbsolutePath()
        }
    }
}
