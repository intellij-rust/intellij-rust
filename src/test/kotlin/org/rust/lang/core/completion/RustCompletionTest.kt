package org.rust.lang.core.completion

class RustCompletionTest : RustCompletionTestBase() {

    override val dataPath = "org/rust/lang/core/completion/fixtures"

    fun testLocalVariable() = checkSoleCompletion()
    fun testFunctionName() = checkSoleCompletion()
    fun testPath() = checkSoleCompletion()
    fun testAnonymousItem() = checkSoleCompletion()
    fun testIncompleteLet() = checkSoleCompletion()
    fun testUseGlob() = checkSoleCompletion()
    fun testTypeParams() = checkSoleCompletion()
    fun testImplMethodType() = checkSoleCompletion()
    fun testStructField() = checkSoleCompletion()
    fun testIncompleteStructField() = checkSoleCompletion()
    fun testEnumField() = checkSoleCompletion()
    fun testWildcardImports() = checkSoleCompletion()

    fun testLocalScope() = checkNoCompletion()
    fun testWhileLet() = checkNoCompletion()


    fun testChildFile() = checkByDirectory {
        openFileInEditor("main.rs")
        executeSoloCompletion()
    }

    fun testParentFile() = checkByDirectory {
        openFileInEditor("foo.rs")
        executeSoloCompletion()
    }

}
