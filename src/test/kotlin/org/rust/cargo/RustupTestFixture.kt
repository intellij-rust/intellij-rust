/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.impl.BaseFixture
import com.intellij.util.EnvironmentUtil
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.tools.Rustup
import org.rust.cargo.toolchain.tools.rustup
import org.rust.cargo.util.DownloadResult
import org.rust.openapiext.RsPathManager
import org.rust.stdext.toPath
import java.nio.file.Paths

// TODO: use it in [org.rust.WithRustup]
open class RustupTestFixture(
    // This property is mutable to allow `com.intellij.testFramework.UsefulTestCase.clearDeclaredFields`
    // set null in `tearDown`
    private var project: Project
) : BaseFixture() {

    val toolchain: RsToolchainBase? by lazy { RsToolchainBase.suggest() }
    val stdlib: VirtualFile? by lazy { (rustup?.downloadStdlib() as? DownloadResult.Ok)?.value }
    private val rustup: Rustup? by lazy { toolchain?.rustup(Paths.get(".")) }

    open val skipTestReason: String?
        get() {
            if (rustup == null) return "No rustup"
            if (stdlib == null) return "No stdlib"
            return null
        }

    override fun setUp() {
        super.setUp()

        setUpAllowedRoots()
        if (toolchain != null) {
            project.rustSettings.modifyTemporary(testRootDisposable) { it.toolchain = toolchain }
        }
    }

    private fun setUpAllowedRoots() {
        stdlib?.let { VfsRootAccess.allowRootAccess(testRootDisposable, it.path) }

        val toolchain = toolchain!!
        val cargoPath = (EnvironmentUtil.getValue("CARGO_HOME")?:"~/.cargo")
            .let { toolchain.expandUserHome(it) }
            .let { toolchain.toLocalPath(it) }
            .toPath()

        VfsRootAccess.allowRootAccess(testRootDisposable, cargoPath.toString())
        // actions-rs/toolchain on CI creates symlink at `~/.cargo` while setting up of Rust toolchain
        val canonicalCargoPath = cargoPath.toRealPath()
        if (cargoPath != canonicalCargoPath) {
            VfsRootAccess.allowRootAccess(testRootDisposable, canonicalCargoPath.toString())
        }

        VfsRootAccess.allowRootAccess(testRootDisposable, RsPathManager.stdlibDependenciesDir().toString())
    }
}
