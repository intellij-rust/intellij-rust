package org.rust.ide.docs

class RustQuickDocumentationTest : RustDocumentationProviderTest() {
    override val dataPath = "org/rust/ide/docs/fixtures/doc"

    fun testFn() = checkDoc()
    fun testDifferentComments() = checkDoc()
    fun testEnumVariant() = checkDoc()
    fun testTraitAssocType() = checkDoc()
    fun testTraitConst() = checkDoc()
    fun testTraitMethod() = checkDoc()
    fun testTraitMethodProvided() = checkDoc()
    fun testInnerDocstring() = checkDoc()

    fun testIssue495() = checkDoc()     // https://github.com/intellij-rust/intellij-rust/issues/495

    private fun checkDoc() = compareByHtml { element, originalElement ->
        RustDocumentationProvider().generateDoc(element, originalElement)
    }
}
