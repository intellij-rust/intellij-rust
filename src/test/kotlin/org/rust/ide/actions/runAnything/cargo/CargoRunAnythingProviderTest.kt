/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.runAnything.cargo

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import org.rust.EmptyDescriptor
import org.rust.ProjectDescriptor
import org.rust.RsTestBase

class CargoRunAnythingProviderTest : RsTestBase() {
    private val provider: CargoRunAnythingProvider = CargoRunAnythingProvider()
    private lateinit var dataContext: DataContext

    override fun setUp() {
        super.setUp()
        dataContext = SimpleDataContext.getProjectContext(project)
    }

    @ProjectDescriptor(EmptyDescriptor::class)
    fun `test do not provide values no rust project linked`() {
        assertEmpty(provider.getValues(dataContext, "cargo "))
    }

    fun `test do not provide values for non-cargo command patterns`() {
        assertEmpty(provider.getValues(dataContext, ""))
    }

    fun `test command completion`() {
        val commands = listOf(
            "new", "publish", "fetch", "yank", "locate-project", "vendor",
            "check", "clean", "metadata", "fix", "version", "owner", "bench",
            "rustc", "run", "rustdoc", "tree", "verify-project", "pkgid",
            "generate-lockfile", "build", "install", "test", "login", "doc",
            "update", "init", "package", "search", "uninstall", "help"
        ).map { "cargo $it" }
        assertSameElements(provider.getValues(dataContext, "car"), commands)
        assertSameElements(provider.getValues(dataContext, "cargo"), commands)
        assertSameElements(provider.getValues(dataContext, "cargo "), commands)
        assertSameElements(provider.getValues(dataContext, "cargo ru"), commands)
        assertSameElements(provider.getValues(dataContext, "cargo run"), commands)
    }

    fun `test options completion`() {
        val runOptions = listOf(
            "color", "target-dir", "manifest-path", "bin", "help", "example",
            "quiet", "message-format", "all-features", "verbose",
            "no-default-features", "offline", "jobs", "target", "locked",
            "package", "features", "frozen", "release"
        ).map { "cargo run --$it" }
        assertSameElements(provider.getValues(dataContext, "cargo run "), runOptions)
        assertSameElements(provider.getValues(dataContext, "cargo run -"), runOptions)
        assertSameElements(provider.getValues(dataContext, "cargo run --"), runOptions)
        assertSameElements(provider.getValues(dataContext, "cargo run --feat"), runOptions)
        assertSameElements(provider.getValues(dataContext, "cargo run feat"), runOptions)
    }
}
