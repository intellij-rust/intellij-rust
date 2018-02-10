/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs

import com.intellij.psi.PsiElement
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.stubs.DefaultStubBuilder
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubTree
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase

class RsStubExistenceTest : RsTestBase() {

    fun `test literal is not stubbed inside statement`() = checkNotStubbed("""
        fn foo() { 0; }
                 //^
    """)

    fun `test expression is not stubbed inside statement`() = checkNotStubbed("""
        fn foo() { 2 + 2; }
                   //^
    """)

    fun `test literal is not stubbed inside function tail expr`() = checkNotStubbed("""
        fn foo() -> i32 { 0 }
                        //^
    """)

    fun `test expression is not stubbed inside function tail expr`() = checkNotStubbed("""
        fn foo() -> i32 { 2 + 2 }
                          //^
    """)

    fun `test literal is not stubbed inside closure tail expr`() = checkNotStubbed("""
        fn foo() {
            || -> i32 { 0 };
        }             //^
    """)

    fun `test expression is not stubbed inside closure tail expr`() = checkNotStubbed("""
        fn foo() {
            || -> i32 { 2 + 2 };
        }               //^
    """)

    fun `test literal is stubbed inside const body`() = checkStubbed("""
        const C: i32 = 0;
                     //^
    """)

    fun `test expression is stubbed inside const body`() = checkStubbed("""
        const C: i32 = 2 + 2;
                       //^
    """)

    fun `test literal is stubbed inside array type`() = checkStubbed("""
        type T = [u8; 1];
                    //^
    """)

    fun `test expression is stubbed inside array type`() = checkStubbed("""
        type T = [u8; 2 + 2];
                      //^
    """)

    private fun checkStubbed(@Language("Rust") code: String) =
        doTest(code, expectStubbed = true)

    private fun checkNotStubbed(@Language("Rust") code: String) =
        doTest(code, expectStubbed = false)

    private fun doTest(@Language("Rust") code: String, expectStubbed: Boolean) {
        InlineFile(code)
        val element = findElementInEditor<StubBasedPsiElement<*>>()

        val rootStub = DefaultStubBuilder().buildStubTree(element.containingFile)
        TreeUtil.bindStubsToTree(StubTree(rootStub as PsiFileStub), myFixture.file.node as FileElement)

        check(element.isStubbed(rootStub.childrenStubs) == expectStubbed) {
            "Target element `${element.text}` " + if (expectStubbed) {
                "is expected to be stubbed, but it is NOT stubbed"
            } else {
                "is expected to be NOT stubbed, but it is stubbed"
            }
        }
    }

    private fun PsiElement.isStubbed(stubs: List<StubElement<*>>): Boolean =
        stubs.any { it.psi.node == node || isStubbed(it.childrenStubs) }
}
