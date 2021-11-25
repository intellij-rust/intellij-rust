/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.flavors

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.isDirectory
import org.rust.stdext.toPath
import java.nio.file.Path

class RsUnixToolchainFlavor : RsToolchainFlavor() {

    override fun getHomePathCandidates(): Sequence<Path> =
        sequenceOf("/usr/local/bin", "/usr/bin")
            .map { it.toPath() }
            .filter { it.isDirectory() }

    override fun isApplicable(): Boolean = super.isApplicable() && SystemInfo.isUnix
}
