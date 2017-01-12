package org.rust.lang.core.completion

import com.intellij.testFramework.LightProjectDescriptor

class RsExternCrateCompletionTest : RsCompletionTestBase() {

    override val dataPath = ""
    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibAndDependencyRustProjectDescriptor

    fun testExternCrate() = checkSingleCompletion("dep_lib_target", """
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
