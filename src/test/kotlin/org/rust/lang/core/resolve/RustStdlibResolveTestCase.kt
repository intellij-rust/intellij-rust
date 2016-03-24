package org.rust.lang.core.resolve

import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.LightProjectDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.rust.cargo.CargoProjectDescription

class RustStdlibResolveTestCase : RustMultiFileResolveTestCaseBase() {

    override fun getProjectDescriptor(): LightProjectDescriptor = WithSdkRustProjectDescriptor()

    override val dataPath = "org/rust/lang/core/resolve/fixtures/stdlib"

    fun testSdkHasSources() {
        assertThat(ModuleRootManager.getInstance(myModule).orderEntries().sdkOnly().classesRoots)
            .overridingErrorMessage("No Rust SDK sources found during test.\n" +
                "Have you run the gradle task to download them?")
            .hasSize(1)
    }

    fun testResolveFs() = doTestResolved("fs/main.rs")
    fun testResolveCollections() = doTestResolved("collections/main.rs")
}
