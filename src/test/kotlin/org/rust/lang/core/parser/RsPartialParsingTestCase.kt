/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.parser

import com.intellij.psi.PsiFile
import org.assertj.core.api.Assertions.assertThat

/**
 * Tests parser recovery (`pin` and `recoverWhile` attributes from `rust.bnf`)
 * by constructing PSI trees from syntactically invalid files.
 */
class RsPartialParsingTestCase : RsParsingTestCaseBase("partial") {

    fun testFn() = doTest(true)
    fun testUseItem() = doTest(true)
    fun testShifts() = doTest(true)
    fun testStructPat() = doTest(true)
    fun testStructDef() = doTest(true)
    fun testEnumVis() = doTest(true)
    fun testImplBody() = doTest(true)
    fun testTraitBody() = doTest(true)
    fun testMatchExpr() = doTest(true)
    fun testStructExprFields() = doTest(true)
    fun testHrtbForLifetimes() = doTest(true)
    fun testNoLifetimeBoundsInGenericArgs() = doTest(true)
    fun testRequireCommas() = doTest(true)

    override fun checkResult(targetDataName: String?, file: PsiFile) {
        checkHasError(file)
        super.checkResult(targetDataName, file)
    }

    private fun checkHasError(file: PsiFile) {
        assertThat(hasError(file))
            .withFailMessage("Invalid file was parsed successfully: ${file.name}")
            .isTrue()
    }
}
