package org.rust.lang.core.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustLanguage

class RustNonPhysicalFileResolveTest : RustResolveTestBase() {
    fun test() {
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

    private fun tryResolveEverything(file: PsiFile) {
        file.accept(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement?) {
                element ?: return
                element.reference?.resolve()
                element.acceptChildren(this)
            }
        })
    }


    private fun memoryOnlyFile(code: String): PsiFile =
        PsiFileFactory.getInstance(project).createFileFromText("foo.rs", RustLanguage, code, false, false).apply {
            assertThat(this.containingFile.virtualFile).isNull()
        }
}
