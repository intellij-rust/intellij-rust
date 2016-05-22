package org.rust.ide.documentation

class RustQuickDocumentationTest : RustDocumentationProviderTest() {
    override val dataPath = "org/rust/ide/documentation/fixtures/doc"

    fun testFn() = checkDoc()
    fun testDifferentComments() = checkDoc()
    fun testEnumVariant() = checkDoc()
    fun testTraitAssocType() = checkDoc()
    fun testTraitConst() = checkDoc()
    fun testTraitMethod() = checkDoc()
    fun testTraitMethodProvided() = checkDoc()

    private fun checkDoc() = compareByHtml { element, originalElement ->
        RustDocumentationProvider().generateDoc(element, originalElement)
    }
}
