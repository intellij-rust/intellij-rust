/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.flavors

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.isDirectory
import org.rust.cargo.toolchain.tools.Rustup
import org.rust.cargo.util.hasExecutable
import org.rust.stdext.toPath
import java.nio.file.Path

class RustupToolchainFlavor : RsToolchainFlavor() {

    override fun getHomePathCandidates(): Sequence<Path> {
        val path = FileUtil.expandUserHome("~/.cargo/bin").toPath()
        return if (path.isDirectory()) {
            sequenceOf(path)
        } else {
            emptySequence()
        }
    }

    override fun isValidToolchainPath(path: Path): Boolean =
        super.isValidToolchainPath(path) && path.hasExecutable(Rustup.NAME)
}
