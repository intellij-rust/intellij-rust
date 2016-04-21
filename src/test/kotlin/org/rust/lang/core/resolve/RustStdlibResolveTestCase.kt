package org.rust.lang.core.resolve

import com.intellij.testFramework.LightProjectDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.rust.cargo.util.cargoProject

class RustStdlibResolveTestCase : RustMultiFileResolveTestCaseBase() {

    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibRustProjectDescriptor()

    override val dataPath = "org/rust/lang/core/resolve/fixtures/stdlib"

    fun testHasStdlibSources() {
        assertThat(myModule.cargoProject?.packages.orEmpty().any { it.name == "std" })
            .overridingErrorMessage("No Rust SDK sources found during test.\n" +
                "Have you run the gradle task to download them?")
            .isTrue()
    }

    fun testResolveFs() = doTestResolved("fs/main.rs")
    fun testResolveCollections() = doTestResolved("collections/main.rs")
}
