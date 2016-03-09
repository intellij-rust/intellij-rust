package org.rust.lang.core.parser

import com.intellij.psi.PsiFile
import org.assertj.core.api.Assertions.assertThat

class RustPartialParsingTestCase : RustParsingTestCaseBase("ill-formed") {
    override fun checkResult(targetDataName: String?, file: PsiFile?) {
        checkHasError(file)
        super.checkResult(targetDataName, file)
    }

    private fun checkHasError(file: PsiFile?) {
        assertThat(hasError(file!!))
            .withFailMessage("Invalid file was parsed successfully: ${file.name}")
            .isTrue()
    }

    // @formatter:off
    fun testFn()            = doTest(true)
    fun testUseItem()       = doTest(true)
    fun testStructPat()     = doTest(true)
    fun testStructDef()     = doTest(true)
    fun testIfExpr()        = doTest(true)
    fun testEnumVis()       = doTest(true)
    // @formatter:on
}
