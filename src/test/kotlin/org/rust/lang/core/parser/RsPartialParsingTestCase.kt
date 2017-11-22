/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.parser

import com.intellij.psi.PsiFile

/**
 * Tests parser recovery (`pin` and `recoverWhile` attributes from `rust.bnf`)
 * by constructing PSI trees from syntactically invalid files.
 */
class RsPartialParsingTestCase : RsParsingTestCaseBase("partial") {

    fun `test fn`() = doTest(true)
    fun `test use item`() = doTest(true)
    fun `test shifts`() = doTest(true)
    fun `test struct pat`() = doTest(true)
    fun `test struct def`() = doTest(true)
    fun `test enum vis`() = doTest(true)
    fun `test impl body`() = doTest(true)
    fun `test trait body`() = doTest(true)
    fun `test match expr`() = doTest(true)
    fun `test struct expr fields`() = doTest(true)
    fun `test hrtb for lifetimes`() = doTest(true)
    fun `test no lifetime bounds in generic args`() = doTest(true)
    fun `test require commas`() = doTest(true)

    override fun checkResult(targetDataName: String?, file: PsiFile) {
        check(hasError(file)) {
            "Invalid file was parsed successfully: ${file.name}"
        }
        super.checkResult(targetDataName, file)
    }

}
