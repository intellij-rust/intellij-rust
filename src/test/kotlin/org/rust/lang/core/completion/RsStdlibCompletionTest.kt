package org.rust.lang.core.completion

import com.intellij.testFramework.LightProjectDescriptor

class RsStdlibCompletionTest : RsCompletionTestBase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibRustProjectDescriptor

    fun testPrelude() = checkSingleCompletion("drop()", """
        fn main() {
            dr/*caret*/
        }
    """)

    fun testPreludeVisibility() = checkNoCompletion("""
        mod m {}
        fn main() {
            m::dr/*caret*/
        }
    """)
}

