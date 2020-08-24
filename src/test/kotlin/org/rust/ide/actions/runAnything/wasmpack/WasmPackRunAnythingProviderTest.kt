/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.runAnything.wasmpack

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import org.rust.EmptyDescriptor
import org.rust.ProjectDescriptor
import org.rust.RsTestBase

class WasmPackRunAnythingProviderTest : RsTestBase() {
    private val provider: WasmPackRunAnythingProvider = WasmPackRunAnythingProvider()
    private lateinit var dataContext: DataContext

    override fun setUp() {
        super.setUp()
        dataContext = SimpleDataContext.getProjectContext(project)
    }

    @ProjectDescriptor(EmptyDescriptor::class)
    fun `test do not provide values no rust project linked`() {
        assertEmpty(provider.getValues(dataContext, "wasm-pack "))
    }

    fun `test do not provide values for unrelated to wasm-pack patterns`() {
        assertEmpty(provider.getValues(dataContext, ""))
    }

    fun `test command completion`() {
        val commands = listOf("build", "publish", "new", "pack", "test").map { "wasm-pack $it" }

        assertSameElements(provider.getValues(dataContext, "wasm-"), commands)
        assertSameElements(provider.getValues(dataContext, "wasm-pack"), commands)
        assertSameElements(provider.getValues(dataContext, "wasm-pack "), commands)
        assertSameElements(provider.getValues(dataContext, "wasm-pack bui"), commands)
        assertSameElements(provider.getValues(dataContext, "wasm-pack build"), commands)
    }

    fun `test options completion`() {
        val buildOptions = listOf(
            "--release", "--out-dir", "--profiling", "--out-name", "--target", "--dev", "--scope"
        ).map { "wasm-pack build $it" }

        assertSameElements(provider.getValues(dataContext, "wasm-pack build "), buildOptions)
        assertSameElements(provider.getValues(dataContext, "wasm-pack build -"), buildOptions)
        assertSameElements(provider.getValues(dataContext, "wasm-pack build --"), buildOptions)
        assertSameElements(provider.getValues(dataContext, "wasm-pack build --feat"), buildOptions)
        assertSameElements(provider.getValues(dataContext, "wasm-pack build feat"), buildOptions)
    }
}
