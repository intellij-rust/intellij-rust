package org.rust.ide.docs

class RustQuickNavigationInfoTest: RustDocumentationProviderTest() {
    override val dataPath = "org/rust/ide/docs/fixtures/nav"

    fun testVariable1() = checkNavigationInfo()
    fun testVariable2() = checkNavigationInfo()
    fun testNestedFunction() = checkNavigationInfo()
    fun testNoComments() = checkNavigationInfo()
    fun testBigSignature() = checkNavigationInfo()

    private fun checkNavigationInfo() = compareByHtml { element, originalElement ->
        RustDocumentationProvider().getQuickNavigateInfo(element, originalElement)
    }
}
