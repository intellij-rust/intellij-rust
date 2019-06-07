/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.hint.ImplementationViewSession
import com.intellij.codeInsight.hint.actions.ShowImplementationsAction
import com.intellij.openapi.editor.ex.EditorEx
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

class RsQuickDefinitionTest : RsTestBase() {

    fun `test struct`() = doTest("""
        struct Foo {
            bar: i32
        }

        fn bar(foo: Foo/*caret*/) {}
    """, """
        struct Foo {
            bar: i32
        }
    """)

    fun `test enum`() = doTest("""
        enum Foo {
            L,
            R(i32)
        }

        fn bar(foo: Foo/*caret*/) {}
    """, """
        enum Foo {
            L,
            R(i32)
        }
    """)

    fun `test trait`() = doTest("""
        trait Foo {
            fn bar(&self);
        }

        fn bar<T: Foo/*caret*/>(foo: T) {}
    """, """
        trait Foo {
            fn bar(&self);
        }
    """)

    fun `test type alias`() = doTest("""
        type Foo = &'static str;

        fn bar(foo: Foo/*caret*/) {}
    """, """
        type Foo = &'static str;
    """)

    fun `test const`() = doTest("""
        const FOO: &'static [&str] = [
            "some test text",
            "another test text"
        ];

        fn main() {
            FOO/*caret*/;
        }
    """, """
        const FOO: &'static [&str] = [
            "some test text",
            "another test text"
        ];
    """)

    fun `test function`() = doTest("""
        fn foo() {
        }

        fn main() {
            foo/*caret*/();
        }
    """, """
        fn foo() {
        }
    """)

    fun `test method`() = doTest("""
        struct Foo;

        impl Foo {
            fn foo(&self) {
            }
        }

        fn bar(foo: Foo) {
            foo.foo/*caret*/();
        }
    """, """
            fn foo(&self) {
            }
    """)

    fun `test field`() = doTest("""
        struct Foo {
            bar: i32
        }

        fn bar(foo: Foo) {
            foo.bar/*caret*/;
        }
    """, """
            bar: i32
    """)

    fun `test let binding`() = doTest("""
        fn main() {
            let a = "
                Some text
            ";
            a/*caret*/;
        }
    """, """
            let a = "
                Some text
            ";
    """)

    fun `test destructuring let binding`() = doTest("""
        fn main() {
            let (a, b) = ("
                Some text
            ", 123);
            a/*caret*/;
        }
    """, """
            let (a, b) = ("
                Some text
            ", 123);
    """)

    fun `test match arm`() = doTest("""
        enum Foo {
            L,
            R(i32)
        }

        fn bar(foo: Foo) {
            match foo {
                L => {},
                R(x) => {
                    x/*caret*/;
                }
            }
        }
    """, """
                R(x) => {
                    x;
                }
    """)

    private fun doTest(@Language("Rust") code: String, expectedRaw: String) {
        InlineFile(code)
        val actualText = performShowImplementationAction()
        checkNotNull(actualText)
        val expected = expectedRaw.lines()
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .joinToString("\n")
        assertEquals(expected, actualText)
    }

    private fun performShowImplementationAction(): String? {
        var actualText: String? = null

        val action = object : ShowImplementationsAction() {
            override fun showImplementations(session: ImplementationViewSession, invokedFromEditor: Boolean, invokedByShortcut: Boolean) {
                if (session.implementationElements.isEmpty()) return
                actualText = session.implementationElements[0].text
            }
        }

        action.performForContext((myFixture.editor as EditorEx).dataContext)
        return actualText
    }
}
