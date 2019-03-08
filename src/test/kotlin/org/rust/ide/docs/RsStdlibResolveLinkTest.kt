/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.psi.PsiManager
import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.psi.ext.RsNamedElement

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsStdlibResolveLinkTest : RsTestBase() {
    fun `test with import`() = doTest("Hash", ".../hash/mod.rs", """
        use std::hash::Hash;

        fn foo<T: Hash>(t: T) {}
          //^
    """)

    fun `test item from prelude`() = doTest("String", ".../string.rs", """
        fn foo(s: String) {}
          //^
    """)

    fun `test crate fqn link`() = doTest("std/index.html", ".../libstd/lib.rs")
    fun `test mod fqn link`() = doTest("std/io/index.html", ".../libstd/io/mod.rs")
    fun `test fqn link with reexport`() = doTest("std/cmp/trait.Eq.html", ".../libcore/cmp.rs")
    fun `test mod fqn link with reexport`() = doTest("std/marker/index.html", ".../libcore/marker.rs")
    fun `test method fqn link with reexport`() = doTest("std/result/enum.Result.html#method.unwrap", ".../libcore/result.rs")
    fun `test macro fqn link`() = doTest("std/macro.println.html", ".../libstd/macros.rs")
    fun `test macro fqn link with reexport`() = doTest("std/macro.assert_eq.html", ".../libcore/macros.rs")

    private fun doTest(link: String, expectedPath: String, @Language("Rust") code: String = DEFAULT_TEXT) {
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

    companion object {
        @Language("Rust")
        private const val DEFAULT_TEXT = """
            struct Foo;
                  //^
        """
    }
}
