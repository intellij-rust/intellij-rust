package org.rust.lang.core.resolve

import com.intellij.testFramework.LightProjectDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.rust.cargo.util.findExternCrateRootByName

class RustStdlibResolveTestCase : RustMultiFileResolveTestCaseBase() {

    override val dataPath = "org/rust/lang/core/resolve/fixtures/stdlib"

    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibRustProjectDescriptor()

    fun testHasStdlibSources() {
        assertThat(myModule.findExternCrateRootByName("std"))
            .overridingErrorMessage("No Rust SDK sources found during test.\n" +
                "Have you run the gradle task to download them?")
            .isNotNull()
    }

    fun testResolveFs() = doTestResolved("fs/main.rs")
    fun testResolveCollections() = doTestResolved("collections/main.rs")
    fun testResolveCore() = doTestResolved("core/main.rs")
    fun testResolvePrelude() = doTestResolved("prelude/main.rs")
    fun testResolveBox() = doTestResolved("box/main.rs")
}
