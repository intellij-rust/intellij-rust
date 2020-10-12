/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.flavors

import com.intellij.util.io.isDirectory
import org.rust.stdext.toPath
import java.io.File
import java.nio.file.Path

object RsSysPathToolchainFlavor : RsToolchainFlavor() {
    override fun getHomePathCandidates(): List<Path> =
        System.getenv("PATH")
            .orEmpty()
            .split(File.pathSeparator)
            .filter { it.isNotEmpty() }
            .map { it.toPath() }
            .filter { it.isDirectory() }
}
