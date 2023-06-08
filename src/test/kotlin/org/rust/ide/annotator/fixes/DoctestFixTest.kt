/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.intellij.lang.annotations.Language
import org.rust.ide.annotator.*

class DoctestFixTest : RsAnnotationTestBase() {
    // Issue https://github.com/intellij-rust/intellij-rust/issues/6790
    fun `test in doctest fix const`() = doTest("Add type i32", """
        const <error descr="Missing type for `const` item">CONST/*caret*/</error> = 1;
    """, """
        const CONST:i32/*caret*/ = 1;
    """)

    // Issue https://github.com/intellij-rust/intellij-rust/issues/6790
    fun `test in doctest add missing fields`() = doTest("Add missing fields", """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
        }
        fn main() {
            let <error descr="Struct pattern does not mention field `c` [E0027]">Foo { a, b, }/*caret*/</error> = foo;
        }
        """, """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
        }
        fn main() {
            let Foo { a, b,c, }/*caret*/ = foo;
        }
        """
    )

    // Issue https://github.com/intellij-rust/intellij-rust/issues/6790
    fun `test in doctest add missing tuple fields`() = doTest("Fill missing arguments", """
        struct S(i32, i32);
        fn main() {
            let _ = S (2, <error descr="This function takes 2 parameters but 1 parameter was supplied [E0061]">/*caret*/)</error>;
        }
    """, """
        struct S(i32, i32);
        fn main() {
            let _ = S (2, 0/*caret*/);
        }
    """)

    // Issue https://github.com/intellij-rust/intellij-rust/issues/6790
    fun `test in doctest fill function arguments`() = doTest("Fill missing arguments", """
        fn foo(a: u32) {}
        fn main() {
            foo(<error>/*caret*/)</error>;
        }
    """, """
        fn foo(a: u32) {}
        fn main() {
            foo(0);
        }
    """)

    override fun createAnnotationFixture(): RsAnnotationTestFixture<Unit> {
        val annotatorClasses = listOf(RsExpressionAnnotator::class, RsSyntaxErrorsAnnotator::class, RsErrorAnnotator::class)
        return RsAnnotationTestFixture(this, myFixture, annotatorClasses = annotatorClasses)
    }

    fun doTest(fixName: String, @Language("Rust") before: String, @Language("Rust") after: String) {
        val template = { text: String ->
            """
                //- lib.rs
                /// Lorem ipsum documentali Rust epmaxle tragi. Funebar
                /// illi _qutiden_ nekto __ulimami_.
                ///
                /// ```
                %s
                /// ```
                ///
                /// Nor amibal queto [tidi] oureteba.
                ///
                /// [tidi]: https://www.example.com
                struct Foo;
            """.trimIndent().format(text.prependIndent("/// "))
        }
        myFixture.setCaresAboutInjection(false)
        checkFixByFileTree(
            fixName,
            before = template(before.trimIndent()),
            after = template(after.trimIndent()),
            checkWarn = false,
            checkInfo = false,
            checkWeakWarn = false,
            preview = null,
        )
    }
}
