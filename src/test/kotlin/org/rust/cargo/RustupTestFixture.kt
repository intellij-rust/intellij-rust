/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.impl.BaseFixture
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.Rustup
import org.rust.cargo.util.DownloadResult
import java.nio.file.Paths

// TODO: use it in [org.rust.WithRustup]
open class RustupTestFixture(
    // This property is mutable to allow `com.intellij.testFramework.UsefulTestCase.clearDeclaredFields`
    // set null in `tearDown`
    private var project: Project
) : BaseFixture() {

    val toolchain: RustToolchain? by lazy { RustToolchain.suggest() }
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
        if (toolchain != null) {
            project.rustSettings.modify { it.toolchain = toolchain }
        }
    }

    override fun tearDown() {
        project.rustSettings.modify { it.toolchain = null }
        super.tearDown()
    }
}
