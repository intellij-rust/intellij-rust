/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.flavors

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.EnvironmentUtil
import com.intellij.util.io.isDirectory
import org.rust.stdext.toPath
import org.rust.stdext.toPathOrNull
import java.nio.file.Path

class CargoToolchainFlavor : RsToolchainFlavor() {
    override fun getHomePathCandidates(): Sequence<Path> {
        val cargoHome = EnvironmentUtil.getValue("CARGO_HOME")?.toPathOrNull()
        val userHome = FileUtil.expandUserHome("~/.cargo/").toPath()
        return sequenceOf(cargoHome, userHome)
            .filterNotNull()
            .map { it.resolve("bin") }
            .filter { it.isDirectory() }
    }
}
