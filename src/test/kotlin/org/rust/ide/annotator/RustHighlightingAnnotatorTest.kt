package org.rust.ide.annotator

class RustHighlightingAnnotatorTest : RustAnnotatorTestBase() {
    override val dataPath = "org/rust/ide/annotator/fixtures/highlighting"

    fun testAttributes() = doTestInfo()
    fun testFieldsAndMethods() = doTestInfo()
    fun testFunctions() = doTestInfo()
    fun testMacro() = doTestInfo()
    fun testMutBinding() = doTestInfo()
    fun testTypeParameters() = doTestInfo()
    fun testFunctionArguments() = doTestInfo()
    fun testContextualKeywords() = doTestInfo()
    fun testTryExpr() = doTestInfo()
}
