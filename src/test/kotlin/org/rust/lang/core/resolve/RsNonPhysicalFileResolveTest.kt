/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.lang.RsLanguage

class RsNonPhysicalFileResolveTest : RsResolveTestBase() {
    fun `test resolve everything`() {
        val code = """
            extern crate foo;

            use foo;
            use std::io;
            use self::bar;
            use super::baz;

            mod not_here;

            mod innner {
                mod not_there_either;
                use super::not_here;
                use super::super::super::quux;
            }

            fn main() {
                let h = ::std::collections::Vec::<i32>new();
            }

            struct S;

            trait T: foo::Bar {}
        """

        tryResolveEverything(memoryOnlyFile(code))
    }

    @MockEdition(Edition.EDITION_2018)
    fun `test macro 2`() {
        val code = """
            macro macro2() {}
            fn main() {
                macro2!();
            }
        """

        checkEverythingIsResolved(memoryOnlyFile(code))
    }

    private fun tryResolveEverything(file: PsiFile) {
        file.accept(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                element.reference?.resolve()
                element.acceptChildren(this)
            }
        })
    }

    private fun checkEverythingIsResolved(file: PsiFile) {
        file.accept(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                element.reference?.let {
                    check(it.resolve() != null) { "$element `${element.text}` should be resolved" }
                }
                element.acceptChildren(this)
            }
        })
    }

    private fun memoryOnlyFile(code: String): PsiFile =
        PsiFileFactory.getInstance(project).createFileFromText("foo.rs", RsLanguage, code, false, false).apply {
            check(this.containingFile.virtualFile == null)
        }
}
