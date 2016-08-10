package org.rust.lang.core.completion

import com.intellij.testFramework.LightProjectDescriptor

class RustStdlibCompletionTest: RustCompletionTestBase() {
    override val dataPath: String get() = "org/rust/lang/core/completion/fixtures/stdlib"

    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibRustProjectDescriptor()

    fun testPrelude() = checkSoleCompletion()
    fun testPreludeVisibility() = checkNoCompletion()
}

