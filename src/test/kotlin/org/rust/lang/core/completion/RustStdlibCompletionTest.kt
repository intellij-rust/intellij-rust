package org.rust.lang.core.completion

import com.intellij.testFramework.LightProjectDescriptor
import org.assertj.core.api.Assertions
import org.rust.cargo.util.cargoProject
import org.rust.lang.core.resolve.RustMultiFileResolveTestCaseBase

class RustStdlibCompletionTest : RustMultiFileResolveTestCaseBase() {
    override val dataPath = "org/rust/lang/core/completion/fixtures/stdlib"

    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibRustProjectDescriptor()

    fun testHasStdlibSources() {
        Assertions.assertThat(myModule.cargoProject?.findExternCrateRootByName("std"))
            .overridingErrorMessage("No Rust SDK sources found during test.\n" +
                "Have you run the gradle task to download them?")
            .isNotNull()
    }

    fun testStdPrelude() = checkSoleCompletion()

    private fun checkSoleCompletion() = checkByFile {
        executeSoloCompletion()
    }

    private fun executeSoloCompletion() {
        val variants = myFixture.completeBasic()
        Assertions.assertThat(variants)
            .withFailMessage("Expected a single completion, but got ${variants?.size}")
            .isNull()
    }
}
