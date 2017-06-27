/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.parser

import com.intellij.psi.PsiFile
import org.assertj.core.api.Assertions.assertThat

class RsCompleteParsingTestCase : RsParsingTestCaseBase("complete") {

    fun testFn() = doTest(true)
    fun testExpr() = doTest(true)
    fun testMod() = doTest(true)
    fun testUseItem() = doTest(true)
    fun testType() = doTest(true)
    fun testShifts() = doTest(true)
    fun testPatterns() = doTest(true)
    fun testAttributes() = doTest(true)
    fun testTraits() = doTest(true)
    fun testMacros() = doTest(true)
    fun testImpls() = doTest(true)
    fun testRanges() = doTest(true)
    fun testExternCrates() = doTest(true)
    fun testExternFns() = doTest(true)
    fun testExternBlock() = doTest(true)
    fun testPrecedence() = doTest(true)
    fun testWayTooManyParens() = doTest(true)
    fun testWayTooManyBraces() = doTest(true)
    fun testEmptyGenerics() = doTest(true)
    fun testStructs() = doTest(true)
    fun testStructLiterals() = doTest(true)
    fun testTryOperator() = doTest(true)
    fun testMatch() = doTest(true)
    fun testOror() = doTest(true)
    fun testAndand() = doTest(true)
    fun testDocComments() = doTest(true)
    fun testAssociatedTypes() = doTest(true)
    fun testLastBlockIsExpression() = doTest(true)
    fun testLoops() = doTest(true)
    fun testBlockBinExpr() = doTest(true)
    fun testMatchCallAmbiguity() = doTest(true)
    fun testVisibility() = doTest(true)

    fun testIssue320() = doTest(true)
    fun testDieselMacros() = doTest(true)

    override fun checkResult(targetDataName: String?, file: PsiFile?) {
        super.checkResult(targetDataName, file)
        assertThat(hasError(file!!))
            .withFailMessage("Error in well formed file ${file.name}")
            .isFalse()
    }

}
