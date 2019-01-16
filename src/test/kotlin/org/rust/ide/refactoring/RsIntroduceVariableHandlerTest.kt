/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.ide.refactoring.introduceVariable.IntroduceVariableTestmarks
import org.rust.lang.core.psi.RsExpr
import org.rust.openapiext.Testmark


class RsIntroduceVariableHandlerTest : RsTestBase() {
    fun `test expression`() = doTest("""
        fn hello() {
            foo(5 + /*caret*/10);
        }
    """, listOf("10", "5 + 10", "foo(5 + 10)"), 0, """
        fn hello() {
            let i = 10;
            foo(5 + i);
        }
    """)

    fun `test caret just after the expression`() = doTest("""
        fn main() {
            1/*caret*/;
        }
    """, emptyList(), 0, """
        fn main() {
            let i = 1;
        }
    """)

    fun `test top level in block`() = doTest("""
        fn main() {
            let _ = {
                1/*caret*/
            };
        }
    """, emptyList(), 0, """
        fn main() {
            let _ = {
                let i = 1;
            };
        }
    """)

    fun `test explicit selection works`() = doTest("""
        fn main() {
            1 + <selection>1</selection>;
        }
    """, emptyList(), 0, """
        fn main() {
            let i = 1;
            1 + i;
        }
    """)

    fun `test replace occurrences forward`() = doTest("""
        fn hello() {
            foo(5 + /*caret*/10);
            foo(5 + 10);
        }
    """, listOf("10", "5 + 10", "foo(5 + 10)"), 1, """
        fn hello() {
            let x = 5 + 10;
            foo(x);
            foo(x);
        }
    """, replaceAll = true)

    fun `test replace occurrences backward`() = doTest("""
        fn main() {
            let a = 1;
            let b = a + 1;
            let c = a + /*caret*/1;
        }
    """, listOf("1", "a + 1"), 1, """
        fn main() {
            let a = 1;
            let x = a + 1;
            let b = x;
            let c = x;
        }
    """, replaceAll = true)

    fun `test statement`() = doTest("""
        fn hello() {
            foo(5 + /*caret*/10);
        }
    """, listOf("10", "5 + 10", "foo(5 + 10)"), 2, """
        fn hello() {
            let foo1 = foo(5 + 10);
        }
    """)

    fun `test match`() = doTest("""
        fn bar() {
            ma/*caret*/tch 5 {
                2 => 2,
                _ => 8,
            };
        }
    """, emptyList(), 0, """
        fn bar() {
            let i = match 5 {
                2 => 2,
                _ => 8,
            };
        }
    """)

    fun `test file`() = doTest("""
        fn read_fle() -> Result<Vec<String, io::Error>> {
            File::op/*caret*/en("res/input.txt")?
        }
    """, listOf("File::open(\"res/input.txt\")", "File::open(\"res/input.txt\")?"), 1, """
        fn read_fle() -> Result<Vec<String, io::Error>> {
            let x = File::open("res/input.txt")?;
        }
    """)

    fun `test ref mut`() = doTest("""
        fn read_file() -> Result<String, Error> {
            let file = File::open("res/input.txt")?;

            file.read_to_string(&mut String:/*caret*/:new())
        }
    """, listOf("String::new()", "&mut String::new()", "file.read_to_string(&mut String::new())"), 0, """
        fn read_file() -> Result<String, Error> {
            let file = File::open("res/input.txt")?;
            let mut string = String::new();

            file.read_to_string(&mut string)
        }
    """)

    fun `test tuple`() = doTest("""
        fn foo((a, b): (u32, u32)) {}
        fn bar() -> (u32, u32) {
            (1, 2)
        }
        fn main() {
            foo(/*caret*/bar());
        }
    """, listOf("bar()", "foo(bar())"), 0, """
        fn foo((a, b): (u32, u32)) {}
        fn bar() -> (u32, u32) {
            (1, 2)
        }
        fn main() {
            let bar1 = bar();
            foo(bar1);
        }
    """)

    fun `test tuple struct`() = doTest("""
        pub struct NodeType(pub u32);

        pub struct Token {
            pub ty: NodeType,
        }

        fn main(t: Token) {
            foo(t./*caret*/ty)
        }
    """, listOf("t.ty", "foo(t.ty)"), 0, """
        pub struct NodeType(pub u32);

        pub struct Token {
            pub ty: NodeType,
        }

        fn main(t: Token) {
            let node_type = t.ty;
            foo(node_type)
        }
    """, mark = IntroduceVariableTestmarks.invalidNamePart)

    // https://github.com/intellij-rust/intellij-rust/issues/2919
    fun `test issue2919`() = doTest("""
        fn main() {
            let i1 = 1;
            let i2 = 1;
            let i3 = 1;
            let (i, x) = (1, 2);

            let z = /*caret*/3 + 4;
            let w = x + 5;
        }
    """, listOf("3", "3 + 4"), 0, """
        fn main() {
            let i1 = 1;
            let i2 = 1;
            let i3 = 1;
            let (i, x) = (1, 2);
            let i4 = 3;

            let z = i4 + 4;
            let w = x + 5;
        }
    """)

    fun `test patterns 1`() = doTest("""
        fn main() {
            let (i, x) = (1, 2);
            let z = /*caret*/3 + 4;
        }
    """, listOf("3", "3 + 4"), 0, """
        fn main() {
            let (i, x) = (1, 2);
            let i1 = 3;
            let z = i1 + 4;
        }
    """)

    fun `test patterns 2`() = doTest("""
        struct S { a: i32 }

        fn main() {
            let s = S { a: 0 };
            match s {
                S { i } => {
                    let x = /*caret*/5 + i;
                }
            }
        }
    """, listOf("5", "5 + i"), 0, """
        struct S { a: i32 }

        fn main() {
            let s = S { a: 0 };
            match s {
                S { i } => {
                    let i1 = 5;
                    let x = i1 + i;
                }
            }
        }
    """)

    fun `test nested scopes`() = doTest("""
        fn main() {
            { { let i = 1; } }
            let i1 = /*caret*/3 + 5;
        }
    """, listOf("3", "3 + 5"), 0, """
        fn main() {
            { { let i = 1; } }
            let i2 = 3;
            let i1 = i2 + 5;
        }
    """)

    fun `test functions and static`() = doTest("""
        fn i() -> i32 { 42 }
        static i1: i32 = 1;
        fn main() {
            let x = i1 + i() + /*caret*/3 + 5;
        }
    """, listOf("3", "i1 + i() + 3", "i1 + i() + 3 + 5"), 0, """
        fn i() -> i32 { 42 }
        static i1: i32 = 1;
        fn main() {
            let i2 = 3;
            let x = i1 + i() + i2 + 5;
        }
    """)


    private fun doTest(
        @Language("Rust") before: String,
        expressions: List<String>,
        target: Int,
        @Language("Rust") after: String,
        replaceAll: Boolean = false
    ) {
        checkByText(before, after) {
            doIntroduce(expressions, target, replaceAll)
        }
    }

    private fun doTest(
        @Language("Rust") before: String,
        expressions: List<String>,
        target: Int,
        @Language("Rust") after: String,
        replaceAll: Boolean = false,
        mark: Testmark
    ) {
        checkByText(before, after) {
            mark.checkHit {
                doIntroduce(expressions, target, replaceAll)
            }
        }
    }

    private fun doIntroduce(expressions: List<String>, target: Int, replaceAll: Boolean) {
        var shownTargetChooser = false
        withMockTargetExpressionChooser(object : ExtractExpressionUi {
            override fun chooseTarget(exprs: List<RsExpr>): RsExpr {
                shownTargetChooser = true
                TestCase.assertEquals(exprs.map { it.text }, expressions)
                return exprs[target]
            }

            override fun chooseOccurrences(expr: RsExpr, occurrences: List<RsExpr>): List<RsExpr> =
                if (replaceAll) occurrences else listOf(expr)
        }) {
            myFixture.performEditorAction("IntroduceVariable")
            if (expressions.isNotEmpty() && !shownTargetChooser) {
                error("Didn't shown chooser")
            }
        }
    }
}
