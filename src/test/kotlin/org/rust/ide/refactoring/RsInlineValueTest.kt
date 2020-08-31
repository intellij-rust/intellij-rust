/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.ide.refactoring.inlineValue.InlineValueMode
import org.rust.ide.refactoring.inlineValue.withMockInlineValueMode

class RsInlineValueTest : RsTestBase() {
    fun `test cannot inline decl without expression`() = checkError("""
        fn foo() {
            let a/*caret*/;
        }
    """, "cannot inline variable without an expression")

    fun `test cannot inline const without expression`() = checkError("""
        fn foo() {
            const /*caret*/a: u32;
        }
    """, "cannot inline constant without an expression")

    fun `test inline variable`() = doTest("""
        fn foo() {
            let /*caret*/a = 5;
            let b = a;
        }
    """, """
        fn foo() {
            let b = 5;
        }
    """)

    fun `test inline constant`() = doTest("""
        const /*caret*/CONST: u32 = 5;

        fn foo() {
            let a = CONST;
        }
    """, """
        fn foo() {
            let a = 5;
        }
    """)

    fun `test inline all usages`() = doTest("""
        fn foo() {
            let /*caret*/a = 5;
            let b = a;
            let c = a;
        }
    """, """
        fn foo() {
            let b = 5;
            let c = 5;
        }
    """)

    fun `test inline all usages from reference`() = doTest("""
        fn foo() {
            let a = 5;
            let b = /*caret*/a;
            let c = a;
        }
    """, """
        fn foo() {
            let b = 5;
            let c = 5;
        }
    """)

    fun `test inline single usage only`() = doTest("""
        fn foo() {
            let a = 5;
            let b = a/*caret*/;
            let c = a;
        }
    """, """
        fn foo() {
            let a = 5;
            let b = 5;
            let c = a;
        }
    """, mode = InlineValueMode.InlineThisOnly)

    fun `test inline and keep original`() = doTest("""
        fn foo() {
            let /*caret*/a = 5;
            let b = a;
            let c = a;
        }
    """, """
        fn foo() {
            let a = 5;
            let b = 5;
            let c = 5;
        }
    """, mode = InlineValueMode.InlineAllAndKeepOriginal)

    fun `test inline usage inside expression`() = doTest("""
        fn foo() {
            let /*caret*/a = 5;
            let b = 2 * a + 1 + a;
        }
    """, """
        fn foo() {
            let b = 2 * 5 + 1 + 5;
        }
    """)

    fun `test inline function call`() = doTest("""
        fn bar() -> u32 { 0 }

        fn foo() {
            let /*caret*/a = bar();
            let b = a + a;
        }
    """, """
        fn bar() -> u32 { 0 }

        fn foo() {
            let b = bar() + bar();
        }
    """)

    fun `test inline struct literal`() = doTest("""
        struct S {
            a: u32,
            b: u64
        }

        fn foo() {
            let /*caret*/a = S { a: 0, b: 0 };
            let b = a;
        }
    """, """
        struct S {
            a: u32,
            b: u64
        }

        fn foo() {
            let b = S { a: 0, b: 0 };
        }
    """)

    fun `test inline method call`() = doTest("""
        struct S {
            a: u32,
            b: u64
        }

        impl S {
            fn foo(&self) {}
        }

        fn foo() {
            let /*caret*/a = S { a: 0, b: 0 };
            let b = a.foo();
        }
    """, """
        struct S {
            a: u32,
            b: u64
        }

        impl S {
            fn foo(&self) {}
        }

        fn foo() {
            let b = S { a: 0, b: 0 }.foo();
        }
    """)

    fun `test inline into field init`() = doTest("""
        struct S {
            a: u32,
        }

        fn foo() {
            let /*caret*/a = 10;
            S { a: a };
        }
    """, """
        struct S {
            a: u32,
        }

        fn foo() {
            S { a: 10 };
        }
    """)

    fun `test inline into field shorthand init`() = doTest("""
        struct S {
            a: u32,
        }

        fn foo() {
            let /*caret*/a = 10;
            S { a };
        }
    """, """
        struct S {
            a: u32,
        }

        fn foo() {
            S { a: 10 };
        }
    """)

    fun `test inline into tuple struct init`() = doTest("""
        struct S(u32);

        fn foo() {
            let /*caret*/a = 10;
            S { 0: a };
        }
    """, """
        struct S(u32);

        fn foo() {
            S { 0: 10 };
        }
    """)

    private fun doTest(@Language("Rust") before: String, @Language("Rust") after: String,
                       mode: InlineValueMode = InlineValueMode.InlineAllAndRemoveOriginal) {
        withMockInlineValueMode(mode) {
            checkEditorAction(before, after, "Inline")
        }
    }

    private fun checkError(@Language("Rust") code: String, errorMessage: String) {
        try {
            checkEditorAction(code, code, "Inline")
            error("no error found, expected $errorMessage")
        } catch (e: Exception) {
            assertEquals(errorMessage, e.message)
        }
    }
}
