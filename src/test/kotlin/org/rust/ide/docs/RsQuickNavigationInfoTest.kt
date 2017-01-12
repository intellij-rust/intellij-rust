package org.rust.ide.docs

class RsQuickNavigationInfoTest : RsDocumentationProviderTest() {
    override val dataPath = "org/rust/ide/docs/fixtures/nav"

    fun testVariable1() = checkNavigationInfo()
    fun testVariable2() = checkNavigationInfo()
    fun testNestedFunction() = checkNavigationInfo()
    fun testNoComments() = checkNavigationInfo()
    fun testBigSignature() = checkNavigationInfo()
    fun testMethod() = checkNavigationInfo()
    fun testTraitMethod() = checkNavigationInfo()
    fun testMultipleWhere() = checkNavigationInfo()
    fun testExpandedSignature() = checkNavigationInfo()

    private fun checkNavigationInfo() = compareByHtml { element, originalElement ->
        RsDocumentationProvider().getQuickNavigateInfo(element, originalElement)
    }
}
