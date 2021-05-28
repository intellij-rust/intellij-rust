/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.flavors

import com.intellij.util.io.isDirectory
import org.rust.stdext.toPathOrNull
import java.io.File
import java.nio.file.Path

class RsSysPathToolchainFlavor : RsToolchainFlavor() {
    override fun getHomePathCandidates(): Sequence<Path> =
        System.getenv("PATH")
            .orEmpty()
            .split(File.pathSeparator)
            .asSequence()
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toPathOrNull() }
            .filter { it.isDirectory() }
}
