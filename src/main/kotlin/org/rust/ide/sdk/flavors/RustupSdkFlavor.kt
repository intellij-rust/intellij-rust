/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.flavors

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.isDirectory
import org.rust.cargo.toolchain.tools.Rustup
import org.rust.ide.sdk.flavors.RsSdkFlavor.Companion.hasExecutable
import org.rust.stdext.toPath
import java.nio.file.Path

object RustupSdkFlavor : RsSdkFlavor {

    override fun getHomePathCandidates(): List<Path> {
        val path = FileUtil.expandUserHome("~/.cargo/bin").toPath()
        return if (path.isDirectory()) {
            listOf(path)
        } else {
            emptyList()
        }
    }

    override fun isValidSdkPath(sdkPath: Path): Boolean =
        super.isValidSdkPath(sdkPath) && sdkPath.hasExecutable(Rustup.NAME)
}
