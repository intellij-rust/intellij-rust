package org.rust.lang.core.parser

import com.intellij.psi.PsiFile
import org.assertj.core.api.Assertions.assertThat

class RustPartialParsingTestCase : RustParsingTestCaseBase("ill-formed") {
    override fun checkResult(targetDataName: String?, file: PsiFile?) {
        checkHasError(file)
        super.checkResult(targetDataName, file)
    }

    private fun checkHasError(file: PsiFile?) {
        assertThat(hasError(file!!)).overridingErrorMessage("Invalid file was parsed sucesfully: " + file.name)
            .isTrue()
    }

    // @formatter:off
    fun testFn()          = doTest(true)
    fun testUseItem()     = doTest(true)
    fun testStructPat()   = doTest(true)
    // @formatter:on
}
