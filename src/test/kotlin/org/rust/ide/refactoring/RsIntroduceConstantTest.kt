/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.refactoring.introduceConstant.ExtractConstantUi
import org.rust.ide.refactoring.introduceConstant.InsertionCandidate
import org.rust.ide.refactoring.introduceConstant.withMockExtractConstantChooser
import org.rust.lang.core.psi.RsExpr

class RsIntroduceConstantTest : RsTestBase() {
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test insertion binary expression`() = doTest("""
        fn foo() {
            let x = /*caret*/5 + 5;
        }
    """, listOf("fn foo", "file"), 0, """
        fn foo() {
            const I: i32 = 5 + 5;
            let x = I;
        }
    """, expression = "5 + 5")

    fun `test replace all`() = doTest("""
        fn foo() {
            let x = /*caret*/5;
            let y = 5;
        }
    """, listOf("fn foo", "file"), 0, """
        fn foo() {
            const I: i32 = 5;
            let x = I;
            let y = I;
        }
    """, replaceAll = true)

    fun `test insertion nested fn`() = doTest("""
        fn foo() {
            fn foo2() {
                let x = /*caret*/5;
            }
        }
    """, listOf("fn foo2", "fn foo", "file"), 1, """
        fn foo() {
            const I: i32 = 5;
            fn foo2() {
                let x = I;
            }
        }
    """)

    fun `test insertion local`() = doTest("""
        fn foo() {
            let x = /*caret*/5;
        }
    """, listOf("fn foo", "file"), 0, """
        fn foo() {
            const I: i32 = 5;
            let x = I;
        }
    """)

    fun `test import`() = doTest("""
        mod a {
            fn foo() {
                let x = /*caret*/5;
            }
        }
    """, listOf("fn foo", "mod a", "file"), 2, """
        const I: i32 = 5;

        mod a {
            use I;

            fn foo() {
                let x = I;
            }
        }
    """)

    fun `test do not import at file scope`() = doTest("""
        fn foo() {
            let x = /*caret*/5;
        }
    """, listOf("fn foo", "file"), 1, """
        const I: i32 = 5;

        fn foo() {
            let x = I;
        }
    """)

    fun `test module inside a function`() = doTest("""
        fn foo() {
            mod bar {
                fn baz() {
                    let a = /*caret*/5;
                }
            }
        }
    """, listOf("fn baz", "mod bar", "file"), 2, """
        const I: i32 = 5;

        fn foo() {
            mod bar {
                use I;

                fn baz() {
                    let a = I;
                }
            }
        }
    """)

    fun `test constant at file scope`() = doTest("""
        const BUFFER: [u8; /*caret*/1000] = [1000; 1000];
    """, listOf("file"), 0, """
        const I: usize = 1000;
        const BUFFER: [u8; I] = [I; I];
    """, replaceAll = true)

    fun `test type alias at file scope`() = doTest("""
        type ARRAY = [u8; /*caret*/1000];
    """, listOf("file"), 0, """
        const I: usize = 1000;

        type ARRAY = [u8; I];
    """)

    fun `test type inside a struct`() = doTest("""
        struct S {
            a: [u8; /*caret*/1000],
            b: [u8; 1000]
        }
    """, listOf("file"), 0, """
        const I: usize = 1000;

        struct S {
            a: [u8; I],
            b: [u8; I]
        }
    """, replaceAll = true)

    private fun doTest(
        @Language("Rust") before: String,
        candidate: List<String>,
        targetCandidate: Int,
        @Language("Rust") after: String,
        expression: String? = null,
        replaceAll: Boolean = false
    ) {
        withMockTargetExpressionChooser(object : ExtractExpressionUi {
            override fun chooseTarget(exprs: List<RsExpr>): RsExpr {
                return expression?.let { e ->
                    exprs.find { it.text == e } ?: throw Exception("Expression '$expression' not found")
                } ?: exprs[0]
            }

            override fun chooseOccurrences(expr: RsExpr, occurrences: List<RsExpr>): List<RsExpr> =
                if (replaceAll) occurrences else listOf(expr)
        }) {
            withMockExtractConstantChooser(object : ExtractConstantUi {
                override fun chooseInsertionPoint(expr: RsExpr, candidates: List<InsertionCandidate>): InsertionCandidate {
                    assertEquals(candidates.map { it.description() }, candidate)
                    return candidates[targetCandidate]
                }
            }) {
                checkEditorAction(before, after, "IntroduceConstant")
            }
        }
    }
}
