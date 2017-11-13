/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring

import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsFile
import org.rust.lang.refactoring.introduceVariable.findCandidateExpressionsToExtract

class RsNameSuggestionsKtTest : RsTestBase() {
    override val dataPath = "org/rust/lang/refactoring/fixtures/introduce_variable/"

    fun testArgumentNames() = doTest("""
        fn foo(a: i32, veryCoolVariableName: i32) {
            a + b
        }

        fn bar() {
            foo(4, 10/*caret*/)
        }
    """) {
        checkSuggestions(it, "i", "name", "variable_name", "cool_variable_name", "very_cool_variable_name")
    }

    fun testNonDirectArgumentNames() = doTest("""
        fn foo(a: i32, veryCoolVariableName: i32) {
            a + b
        }

        fn bar() {
            foo(4, 1/*caret*/0 + 2)
        }
    """) {
        checkSuggestions(it, "i")
    }


    fun testFunctionNames() = doTest("""
        fn foo(a: i32, veryCoolVariableName: i32) -> i32 {
            a + b
        }

        fn bar() {
            f/*caret*/oo(4, 10 + 2)
        }
    """) {
        checkSuggestions(it, "i", "foo")
    }

    fun testStringNew() = doTest("""
        fn read_file() -> Result<String, Error> {
            let file = File::open("res/input.txt")?;

            file.read_to_string(&mut String:/*caret*/:new())?;
    }""") {
        checkSuggestions(it, "string", "new")
    }

    fun testLocalNames() = doTest("""
        fn foo() {
            let string = "hi";
            let b = String:/*caret*/:new();
        }
    """) {
        checkSuggestions(it, "new")
    }

    fun testFunctionCallAsArgument() = doTest("""
        fn foo(board_size: i32) {}

        fn bar() {
            foo(Default::de/*caret*/fault());
        }
    """) {
        checkSuggestions(it, "size", "board_size")
    }

    fun testStructLiteral() = doTest("""
        struct Foo {
            bar: i32,
            baz: i32,
        }

        impl Foo {
            fn new() -> Foo {
                Foo{bar: 5, baz: 1/*caret*/0}
            }
        }
        """) {
        checkSuggestions(it, "i", "baz")
    }

    fun testGenericPath() = doTest("""
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
        """) {
        checkSuggestions(it, "f", "foo", "new")
    }

    private fun doTest(@Language("Rust") before: String, action: (Set<String>) -> Unit) {
        InlineFile(before).withCaret()
        openFileInEditor("main.rs")
        val expr = findCandidateExpressionsToExtract(myFixture.editor, myFixture.file as RsFile).first()
        action(expr.suggestNames())
    }

    private fun checkSuggestions(actual: Set<String>, vararg expected: String) {
        check(actual == expected.toSet())
    }
}
