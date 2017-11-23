/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.parser

import com.intellij.psi.PsiFile

class RsCompleteParsingTestCase : RsParsingTestCaseBase("complete") {

    fun `test fn`() = doTest(true)
    fun `test turbo`() = doTest(true)
    fun `test expr`() = doTest(true)
    fun `test mod`() = doTest(true)
    fun `test use item`() = doTest(true)
    fun `test type`() = doTest(true)
    fun `test shifts`() = doTest(true)
    fun `test patterns`() = doTest(true)
    fun `test attributes`() = doTest(true)
    fun `test traits`() = doTest(true)
    fun `test macros`() = doTest(true)
    fun `test impls`() = doTest(true)
    fun `test ranges`() = doTest(true)
    fun `test extern crates`() = doTest(true)
    fun `test extern fns`() = doTest(true)
    fun `test extern block`() = doTest(true)
    fun `test precedence`() = doTest(true)
    fun `test way too many parens`() = doTest(true)
    fun `test way too many braces`() = doTest(true)
    fun `test empty generics`() = doTest(true)
    fun `test structs`() = doTest(true)
    fun `test struct literals`() = doTest(true)
    fun `test try operator`() = doTest(true)
    fun `test match`() = doTest(true)
    fun `test oror`() = doTest(true)
    fun `test andand`() = doTest(true)
    fun `test comment binding`() = doTest(true)
    fun `test associated types`() = doTest(true)
    fun `test last block is expression`() = doTest(true)
    fun `test loops`() = doTest(true)
    fun `test block bin expr`() = doTest(true)
    fun `test match call ambiguity`() = doTest(true)
    fun `test visibility`() = doTest(true)

    fun `test issue320`() = doTest(true)
    fun `test diesel macros`() = doTest(true)

    fun `test attrs in exprs`() = doTest(true)

    override fun checkResult(targetDataName: String?, file: PsiFile?) {
        super.checkResult(targetDataName, file)
        check(!hasError(file!!)){
            "Error in well formed file ${file.name}"
        }
    }

}
