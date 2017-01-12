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

    fun testIssue495() = checkDoc()     // https://github.com/intellij-rust/intellij-rust/issues/495

    private fun checkDoc() = compareByHtml { element, originalElement ->
        RustDocumentationProvider().generateDoc(element, originalElement)
    }
}
