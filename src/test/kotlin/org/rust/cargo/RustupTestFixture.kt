/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.impl.BaseFixture
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.toolchain.RsToolchain
import org.rust.cargo.toolchain.Rustup
import org.rust.cargo.toolchain.rustup
import org.rust.cargo.util.DownloadResult
import java.nio.file.Paths

// TODO: use it in [org.rust.WithRustup]
open class RustupTestFixture(
    // This property is mutable to allow `com.intellij.testFramework.UsefulTestCase.clearDeclaredFields`
    // set null in `tearDown`
    private var project: Project
) : BaseFixture() {

    val toolchain: RsToolchain? by lazy { RsToolchain.suggest() }
    val rustup: Rustup? by lazy { toolchain?.rustup(Paths.get(".")) }
    val stdlib: VirtualFile? by lazy { (rustup?.downloadStdlib() as? DownloadResult.Ok)?.value }

    open val skipTestReason: String?
        get() {
            if (rustup == null) return "No rustup"
            if (stdlib == null) return "No stdlib"
            return null
        }

    override fun setUp() {
        super.setUp()
        stdlib?.let { VfsRootAccess.allowRootAccess(testRootDisposable, it.path) }
        addCargoHomeToAllowedRoots()
        if (toolchain != null) {
            project.rustSettings.modifyTemporary(testRootDisposable) { it.toolchain = toolchain }
        }
    }

    private fun addCargoHomeToAllowedRoots() {
        val cargoHome = Paths.get(FileUtil.expandUserHome("~/.cargo"))
        VfsRootAccess.allowRootAccess(testRootDisposable, cargoHome.toString())
        // actions-rs/toolchain on CI creates symlink at `~/.cargo` while setting up of Rust toolchain
        val canonicalCargoHome = cargoHome.toRealPath()
        if (cargoHome != canonicalCargoHome) {
            VfsRootAccess.allowRootAccess(testRootDisposable, canonicalCargoHome.toString())
        }
    }
}
