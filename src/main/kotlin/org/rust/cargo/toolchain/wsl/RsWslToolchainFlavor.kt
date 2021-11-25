/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.wsl

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import org.rust.cargo.toolchain.flavors.RsToolchainFlavor
import org.rust.openapiext.computeWithCancelableProgress
import org.rust.openapiext.isDispatchThread
import org.rust.stdext.resolveOrNull
import java.nio.file.Path

class RsWslToolchainFlavor : RsToolchainFlavor() {

    override fun getHomePathCandidates(): Sequence<Path> = sequence {
        val distributions = WslDistributionManager.getInstance().computeInstalledDistributions()
        for (distro in distributions) {
            yieldAll(distro.getHomePathCandidates())
        }
    }

    override fun isApplicable(): Boolean = RsWslToolchain.isWslEnabled

    override fun isValidToolchainPath(path: Path): Boolean =
        WslDistributionManager.isWslPath(path.toString()) && super.isValidToolchainPath(path)

    override fun hasExecutable(path: Path, toolName: String): Boolean = path.hasExecutableOnWsl(toolName)

    override fun pathToExecutable(path: Path, toolName: String): Path = path.pathToExecutableOnWsl(toolName)
}

fun WSLDistribution.getHomePathCandidates(): Sequence<Path> = sequence {
    @Suppress("UnstableApiUsage")
    val root = uncRootPath
    val environment = compute("Getting environment variables...") { environment }

    val home = environment["HOME"]
    val remoteCargoPath = home?.let { "$it/.cargo/bin" }
    val localCargoPath = remoteCargoPath?.let { root.resolve(it) }
    if (localCargoPath?.exists() == true) {
        yield(localCargoPath)
    }

    val sysPath = environment["PATH"]
    for (remotePath in sysPath.orEmpty().split(":")) {
        if (remotePath.isEmpty()) continue
        val localPath = root.resolveOrNull(remotePath) ?: continue
        if (!localPath.isDirectory()) continue
        yield(localPath)
    }

    for (remotePath in listOf("/usr/local/bin", "/usr/bin")) {
        val localPath = root.resolve(remotePath)
        if (!localPath.isDirectory()) continue
        yield(localPath)
    }
}

fun <T> compute(
    @Suppress("UnstableApiUsage") @ProgressTitle title: String,
    getter: () -> T
): T = if (isDispatchThread) {
    val project = ProjectManager.getInstance().defaultProject
    project.computeWithCancelableProgress(title, getter)
} else {
    getter()
}
