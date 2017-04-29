package org.rust.ide.docs

class RsQuickDocumentationTest : RsDocumentationProviderTest() {
    override val dataPath = "org/rust/ide/docs/fixtures/doc"

    fun testFn() = checkDoc()
    fun testDifferentComments() = checkDoc()
    fun testEnumVariant() = checkDoc()
    fun testTraitAssocType() = checkDoc()
    fun testTraitConst() = checkDoc()
    fun testTraitMethod() = checkDoc()
    fun testTraitMethodProvided() = checkDoc()
    fun testModInnerDocstring() = checkDoc()
    fun testFnInnerDocstring() = checkDoc()
    fun testFileInnerDocstring() = checkDoc()
    fun testQualifiedName() = checkDoc()

    fun testIssue495() = checkDoc()     // https://github.com/intellij-rust/intellij-rust/issues/495

    fun testFnArg() = checkDoc()
    fun testVariable() = checkDoc()
    fun testGenericEnumVariable() = checkDoc()
    fun testGenericStructVariable() = checkDoc()
    fun testTupleDestructuring() = checkDoc()
    fun testConditionalBinding() = checkDoc()

    private fun checkDoc() = compareByHtml { element, originalElement ->
        RsDocumentationProvider().generateDoc(element, originalElement)
    }
}
