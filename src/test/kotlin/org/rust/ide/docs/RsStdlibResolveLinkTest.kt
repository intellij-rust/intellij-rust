/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.psi.ext.RsNamedElement

// BACKCOMPAT: Rust 1.46
//  Since Rust 1.47 layout of stdlib was changed.
//  In general, `lib%lib_name%` was replaced with `%lib_name%/src`
@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsStdlibResolveLinkTest : RsTestBase() {
    fun `test with import`() = doTest("Hash", "...hash/mod.rs", """
        use std::hash::Hash;

        fn foo<T: Hash>(t: T) {}
          //^
    """)

    fun `test item from prelude`() = doTest("String", "...string.rs", """
        fn foo(s: String) {}
          //^
    """)

    fun `test crate fqn link`() = doTest("std/index.html", "...libstd/lib.rs|...std/src/lib.rs")
    fun `test mod fqn link`() = doTest("std/io/index.html", "...libstd/io/mod.rs|...std/src/io/mod.rs")
    fun `test fqn link with reexport`() = doTest("std/cmp/trait.Eq.html", "...libcore/cmp.rs|...core/src/cmp.rs")
    fun `test mod fqn link with reexport`() = doTest("std/marker/index.html", "...libcore/marker.rs|...core/src/marker.rs")
    fun `test method fqn link with reexport`() = doTest("std/result/enum.Result.html#method.unwrap", "...libcore/result.rs|...core/src/result.rs")
    fun `test macro fqn link`() = doTest("std/macro.println.html", "...libstd/macros.rs|...std/src/macros.rs")
    fun `test macro fqn link with reexport`() = doTest("std/macro.assert_eq.html", "...libcore/macros.rs|...libcore/macros/mod.rs|...core/src/macros/mod.rs")

    fun `test fqn link in keyword doc`() = doTest("std/future/trait.Future.html", "...libcore/future/future.rs|...core/src/future/future.rs", """
        async fn foo() {}
        //^
    """, PsiElement::class.java)

    private fun doTest(
        link: String,
        expectedPaths: String,
        @Language("Rust") code: String = DEFAULT_TEXT,
        psiClass: Class<out PsiElement> = RsNamedElement::class.java
    ) {
        val paths = expectedPaths.split("|")
        for (expectedPath in paths) {
            check(expectedPath.startsWith("...")) {
                "Expected path should start with '...', but '$expectedPath' was found"
            }
        }
        InlineFile(code)
        val context = findElementInEditor(psiClass, "^")
        val element = RsDocumentationProvider()
            .getDocumentationElementForLink(PsiManager.getInstance(project), link, context)
            ?: error("Failed to resolve link $link")

        val actualFile = element.containingFile.virtualFile

        check(paths.any { actualFile.path.endsWith(it.drop(3)) }) {
            if (paths.size == 1) {
                "Should resolve to ${paths.single()}, was ${actualFile.path} instead"
            } else {
                "Should resolve to one of $paths, was ${actualFile.path} instead"
            }
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
