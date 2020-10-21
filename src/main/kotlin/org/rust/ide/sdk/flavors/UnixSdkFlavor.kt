/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.flavors

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.isDirectory
import org.rust.stdext.toPath
import java.nio.file.Path

object UnixSdkFlavor : RsSdkFlavor {
    override fun isApplicable(): Boolean = SystemInfo.isUnix

    override fun getHomePathCandidates(): List<Path> =
        listOf("/usr/local/bin", "/usr/bin")
            .map { it.toPath() }
            .filter { it.isDirectory() }
}
