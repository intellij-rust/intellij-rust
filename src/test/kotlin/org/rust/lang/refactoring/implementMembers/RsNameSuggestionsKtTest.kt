/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.implementMembers

import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsFile
import org.rust.lang.refactoring.introduceVariable.findCandidateExpressionsToExtract
import org.rust.lang.refactoring.introduceVariable.suggestedNames

class RsNameSuggestionsKtTest : RsTestBase() {
    override val dataPath = "org/rust/lang/refactoring/fixtures/introduce_variable/"

    fun `test argument names`() = doTest("""
        fn foo(a: i32, veryCoolVariableName: i32) {
            a + b
        }

        fn bar() {
            foo(4, 10/*caret*/)
        }
    """,
        listOf("i", "name", "variable_name", "cool_variable_name", "very_cool_variable_name")
    )

    fun `test non direct argument names`() = doTest("""
        fn foo(a: i32, veryCoolVariableName: i32) {
            a + b
        }

        fn bar() {
            foo(4, 1/*caret*/0 + 2)
        }
    """,
        listOf("i")
    )


    fun `test function names`() = doTest("""
        fn foo(a: i32, veryCoolVariableName: i32) -> i32 {
            a + b
        }

        fn bar() {
            f/*caret*/oo(4, 10 + 2)
        }
    """,
        listOf("i", "foo")
    )

    fun `test string new`() = doTest("""
        fn read_file() -> Result<String, Error> {
            let file = File::open("res/input.txt")?;

            file.read_to_string(&mut String:/*caret*/:new())?;
    }""",
        listOf("string")
    )

    fun `test local names`() = doTest("""
        fn foo() {
            let string = "hi";
            let b = String:/*caret*/:new();
        }
    """,
        listOf()
    )

    fun `test function call as argument`() = doTest("""
        fn foo(board_size: i32) {}

        fn bar() {
            foo(Default::de/*caret*/fault());
        }
    """,
        listOf("size", "board_size")
    )

    fun `test struct literal`() = doTest("""
        struct Foo {
            bar: i32,
            baz: i32,
        }

        impl Foo {
            fn new() -> Foo {
                Foo{bar: 5, baz: 1/*caret*/0}
            }
        }
    """,
        listOf("i", "baz")
    )

    fun `test generic path`() = doTest("""
        struct Foo<T> {
            t: T,
        }

        impl <T> Foo<T> {
            fn new(t: T) -> Foo<T> {
                Foo {t: t}
            }
        }

        fn bar() {
            Foo:/*caret*/:<i32>::new(10)
        }
        """,
        listOf("foo")
    )

    fun `test don't blow up on non-nominal type`() = doTest("""
        fn main() {
            (1, 2, 3)/*caret*/
        }
    """,
        listOf()
    )

    fun `test don't blow up on non-trivial parameter name`() = doTest("""
        fn foo((x, y): (i32, i32)) {}
        fn main() {
            foo((0, /*caret*/ 1))
        }
    """,
        listOf()
    )

    private fun doTest(@Language("Rust") before: String, expected: List<String>) {
        InlineFile(before).withCaret()
        openFileInEditor("main.rs")
        val expr = findCandidateExpressionsToExtract(myFixture.editor, myFixture.file as RsFile).first()
        val suggestedNames = expr.suggestedNames()
        TestCase.assertEquals(expected, suggestedNames.all.toList())
    }
}
