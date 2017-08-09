/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.psi.PsiManager
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.ext.RsNamedElement

class RsStdlibResolveLinkTest : RsTestBase() {

    override fun getProjectDescriptor() = WithStdlibRustProjectDescriptor

    fun `test with import`() = doTest("""
        use std::hash::Hash;

        fn foo<T: Hash>(t: T) {}
          //^
    """, "Hash", ".../hash/mod.rs")

    fun `test item from prelude`() = doTest("""
        fn foo(s: String) {}
          //^
    """, "String", ".../string.rs")

    private fun doTest(@Language("Rust") code: String, link: String, expectedPath: String) {
        check(expectedPath.startsWith("...")) {
            "Expected path should start with '...', but '$expectedPath' was found"
        }
        InlineFile(code)
        val context = findElementInEditor<RsNamedElement>("^")
        val element = RsDocumentationProvider()
            .getDocumentationElementForLink(PsiManager.getInstance(project), link, context)
            ?: error("Failed to resolve link $link")

        val actualFile = element.containingFile.virtualFile

        check(actualFile.path.endsWith(expectedPath.drop(3))) {
            "Should resolve to $expectedPath, was ${actualFile.path} instead"
        }
    }
}
