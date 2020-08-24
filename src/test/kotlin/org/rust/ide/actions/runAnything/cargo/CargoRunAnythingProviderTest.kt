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
            "build", "check", "clean", "doc", "run", "test", "bench", "update", "search", "publish", "install"
        ).map { "cargo $it" }
        assertSameElements(provider.getValues(dataContext, "car"), commands)
        assertSameElements(provider.getValues(dataContext, "cargo"), commands)
        assertSameElements(provider.getValues(dataContext, "cargo "), commands)
        assertSameElements(provider.getValues(dataContext, "cargo ru"), commands)
        assertSameElements(provider.getValues(dataContext, "cargo run"), commands)
    }

    fun `test options completion`() {
        val runOptions = listOf(
            "--bin", "--example", "--package", "--jobs", "--release", "--manifest-path", "--verbose", "--quiet",
            "--features", "--all-features", "--no-default-features"
        ).map { "cargo run $it" }
        assertSameElements(provider.getValues(dataContext, "cargo run "), runOptions)
        assertSameElements(provider.getValues(dataContext, "cargo run -"), runOptions)
        assertSameElements(provider.getValues(dataContext, "cargo run --"), runOptions)
        assertSameElements(provider.getValues(dataContext, "cargo run --feat"), runOptions)
        assertSameElements(provider.getValues(dataContext, "cargo run feat"), runOptions)
    }
}
