package org.rust.lang.core.parser

import com.intellij.psi.PsiFile
import org.assertj.core.api.Assertions.assertThat

class RustCompleteParsingTestCase : RustParsingTestCaseBase("well-formed") {
    override fun checkResult(targetDataName: String?, file: PsiFile?) {
        assertThat(hasError(file!!))
            .withFailMessage("Error in well formed file ${file.name}")
            .isFalse()
        super.checkResult(targetDataName, file)
    }

    // @formatter:off
    fun testFn()                    = doTest(true)
    fun testExpr()                  = doTest(true)
    fun testMod()                   = doTest(true)
    fun testUseItem()               = doTest(true)
    fun testType()                  = doTest(true)
    fun testShifts()                = doTest(true)
    fun testPatterns()              = doTest(true)
    fun testAttributes()            = doTest(true)
    fun testTraits()                = doTest(true)
    fun testMacros()                = doTest(true)
    fun testImpls()                 = doTest(true)
    fun testSuper()                 = doTest(true)
    fun testRanges()                = doTest(true)
    fun testExternCrates()          = doTest(true)
    fun testExternFns()             = doTest(true)
    fun testSuperPaths()            = doTest(true)
    fun testPrecedence()            = doTest(true)
    // @formatter:on
}
