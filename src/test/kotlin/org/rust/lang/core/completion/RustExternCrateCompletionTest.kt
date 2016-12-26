package org.rust.lang.core.completion

import com.intellij.testFramework.LightProjectDescriptor

class RustExternCrateCompletionTest : RustCompletionTestBase() {

    override val dataPath = ""
    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibAndDependencyRustProjectDescriptor

    fun testExternCrate() = checkSingleCompletion("dep_lib", """
        extern crate dep_l/*caret*/
    """)

    fun testExternCrateDoesntSuggestStdlib() = checkNoCompletion("""
        extern crate cor/*caret*/
    """)

    fun testExternCrateDoesntSuggestOurCrate() = checkNoCompletion("""
        extern crate tes/*caret*/
    """)

    fun testExternCrateDoesntSuggestTransitiveDependency() = checkNoCompletion("""
        extern crate trans_l/*caret*/
    """)
}
