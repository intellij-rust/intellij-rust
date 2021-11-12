/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.suggested

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.refactoring.suggested.SuggestedRefactoringExecution
import com.intellij.refactoring.suggested._suggestedChangeSignatureNewParameterValuesForTests
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsPsiFactory

class RsChangeSignatureSuggestedRefactoringTest : RsSuggestedRefactoringTestBase() {
    fun `test unavailable when changing function modifiers`() = doUnavailableTest("""
        /*caret*/fn foo() {}
    """) { myFixture.type("async ") }

    fun `test unavailable when changing return type`() = doUnavailableTest("""
        fn foo()/*caret*/ {}
    """) { myFixture.type(" -> u32") }

    fun `test unavailable when changing function parameter type`() = doUnavailableTest("""
        fn foo(a: /*caret*/u32) {}
    """) {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE)
        myFixture.type("i")
    }

    fun `test unavailable on inner binding change`() = doUnavailableTest("""
        struct S {
            a: u32
        }

        fn foo(S { a/*caret*/ }: S) {}
    """) {
        myFixture.type(": b")
    }

    fun `test unavailable when changing a parameter without a binding`() = doUnavailableTest("""
        struct S {}

        fn foo(/*caret*/s: S) {}

        fn bar() {
            foo(S {});
        }
    """) {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE)
        myFixture.type("S {}")
    }

    fun `test unavailable on function with cfg-disabled parameters`() = doUnavailableTest("""
        fn foo(#[cfg(feature = "foo")] a: u32, /*caret*/) {}
    """) { myFixture.type("b: u32") }

    fun `test rename and modify signature`() = doTestChangeSignature("""
        fn foo/*caret*/() {}

        fn bar() {
            foo();
        }
    """, """
        fn foox(a: u32) {}

        fn bar() {
            foox();
        }
    """, "foox", {
        myFixture.type("x")
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT)
        myFixture.type("a: u32")
    }, """
Old:
  'foo' (modified)
  '('
  LineBreak('', false)
  ')'
New:
  'foox' (modified)
  '('
  LineBreak('', true)
  Group (added):
    'a'
    ': '
    'u32'
  LineBreak('', false)
  ')'
  """.trimIndent())

    fun `test add parameter`() = doTestChangeSignature("""
        fn foo(/*caret*/) {}

        fn bar() {
            foo();
        }
    """, """
        fn foo(a: u32) {}

        fn bar() {
            foo();
        }
    """, "foo", { myFixture.type("a: u32") }, """
Old:
  'foo'
  '('
  LineBreak('', false)
  ')'
New:
  'foo'
  '('
  LineBreak('', true)
  Group (added):
    'a'
    ': '
    'u32'
  LineBreak('', false)
  ')'
    """.trimIndent())

    fun `test add two parameters`() = doTestChangeSignature("""
        fn foo(/*caret*/) {}

        fn bar() {
            foo();
        }
    """, """
        fn foo(a: u32, b: u32) {}

        fn bar() {
            foo(, );
        }
    """, "foo", { myFixture.type("a: u32, b: u32") }, """
Old:
  'foo'
  '('
  LineBreak('', false)
  ')'
New:
  'foo'
  '('
  LineBreak('', true)
  Group (added):
    'a'
    ': '
    'u32'
  ','
  LineBreak(' ', true)
  Group (added):
    'b'
    ': '
    'u32'
  LineBreak('', false)
  ')'
    """.trimIndent())

    fun `test prepend parameter`() = doTestChangeSignature("""
        fn foo(/*caret*/b: &str) {}

        fn bar() {
            foo("");
        }
    """, """
        fn foo(a: u32, b: &str) {}

        fn bar() {
            foo(, "");
        }
    """, "foo", {
        myFixture.type("a: u32, ")
    }, """
Old:
  'foo'
  '('
  LineBreak('', true)
  Group:
    'b'
    ': '
    '&str'
  LineBreak('', false)
  ')'
New:
  'foo'
  '('
  LineBreak('', true)
  Group (added):
    'a'
    ': '
    'u32'
  ','
  LineBreak(' ', true)
  Group:
    'b'
    ': '
    '&str'
  LineBreak('', false)
  ')'
    """.trimIndent())

    fun `test rename parameter`() = doTestChangeSignature("""
        fn foo(a/*caret*/: u32) {
            let b = a;
        }
    """, """
        fn foo(ax: u32) {
            let b = ax;
        }
    """, "foo", { myFixture.type("x") }, """
Old:
  'foo'
  '('
  LineBreak('', true)
  Group:
    'a' (modified)
    ': '
    'u32'
  LineBreak('', false)
  ')'
New:
  'foo'
  '('
  LineBreak('', true)
  Group:
    'ax' (modified)
    ': '
    'u32'
  LineBreak('', false)
  ')'
    """.trimIndent())

    fun `test rename parameter while other parameter has no binding`() = doTestChangeSignature("""
        struct S {}

        fn foo(a/*caret*/: u32, S {}: S) {
            let b = a;
        }
    """, """
        struct S {}

        fn foo(ax: u32, S {}: S) {
            let b = ax;
        }
    """, "foo", { myFixture.type("x") }, """
Old:
  'foo'
  '('
  LineBreak('', true)
  Group:
    'a' (modified)
    ': '
    'u32'
  ','
  LineBreak(' ', true)
  Group:
    'S {}'
    ': '
    'S'
  LineBreak('', false)
  ')'
New:
  'foo'
  '('
  LineBreak('', true)
  Group:
    'ax' (modified)
    ': '
    'u32'
  ','
  LineBreak(' ', true)
  Group:
    'S {}'
    ': '
    'S'
  LineBreak('', false)
  ')'
    """.trimIndent())

    fun `test remove parameter`() = doTestChangeSignature("""
        fn foo(/*caret*/a: u32) {}

        fn bar() {
            foo(0);
        }
    """, """
        fn foo() {}

        fn bar() {
            foo();
        }
    """, "foo", {
        repeat(6) {
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE)
        }
    }, """
Old:
  'foo'
  '('
  LineBreak('', true)
  Group (removed):
    'a'
    ': '
    'u32'
  LineBreak('', false)
  ')'
New:
  'foo'
  '('
  LineBreak('', false)
  ')'
    """.trimIndent())

    fun `test remove and swap parameters`() = doTestChangeSignature("""
        fn foo(a: u32, /*caret*/b: u32, c: u32) {}

        fn bar() {
            foo(1, 2, 3);
        }
    """, """
        fn foo(c: u32, a: u32) {}

        fn bar() {
            foo(3, 1);
        }
    """, "foo", {
        repeat(8) {
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE)
        }
        myFixture.performEditorAction(IdeActions.MOVE_ELEMENT_LEFT)
    }, """
Old:
  'foo'
  '('
  LineBreak('', true)
  Group (moved):
    'a'
    ': '
    'u32'
  ','
  LineBreak(' ', true)
  Group (removed):
    'b'
    ': '
    'u32'
  ','
  LineBreak(' ', true)
  Group:
    'c'
    ': '
    'u32'
  LineBreak('', false)
  ')'
New:
  'foo'
  '('
  LineBreak('', true)
  Group:
    'c'
    ': '
    'u32'
  ','
  LineBreak(' ', true)
  Group (moved):
    'a'
    ': '
    'u32'
  LineBreak('', false)
  ')'
    """.trimIndent())

    fun `test swap parameters`() = doTestChangeSignature("""
        fn foo(/*caret*/a: u32, b: u32) {}

        fn bar() {
            foo(0, 1);
        }
    """, """
        fn foo(b: u32, a: u32) {}

        fn bar() {
            foo(1, 0);
        }
    """, "foo", {
        myFixture.performEditorAction(IdeActions.MOVE_ELEMENT_RIGHT)
    }, """
Old:
  'foo'
  '('
  LineBreak('', true)
  Group (moved):
    'a'
    ': '
    'u32'
  ','
  LineBreak(' ', true)
  Group:
    'b'
    ': '
    'u32'
  LineBreak('', false)
  ')'
New:
  'foo'
  '('
  LineBreak('', true)
  Group:
    'b'
    ': '
    'u32'
  ','
  LineBreak(' ', true)
  Group (moved):
    'a'
    ': '
    'u32'
  LineBreak('', false)
  ')'
    """.trimIndent())

    fun `test swap and rename parameters`() = doTestChangeSignature("""
        fn foo(/*caret*/a: u32, b: u32) {}

        fn bar() {
            foo(0, 1);
        }
    """, """
        fn foo(b: u32, xa: u32) {}

        fn bar() {
            foo(1, 0);
        }
    """, "foo", {
        myFixture.performEditorAction(IdeActions.MOVE_ELEMENT_RIGHT)
        myFixture.type("x")
    }, """
Old:
  'foo'
  '('
  LineBreak('', true)
  Group (moved):
    'a' (modified)
    ': '
    'u32'
  ','
  LineBreak(' ', true)
  Group:
    'b'
    ': '
    'u32'
  LineBreak('', false)
  ')'
New:
  'foo'
  '('
  LineBreak('', true)
  Group:
    'b'
    ': '
    'u32'
  ','
  LineBreak(' ', true)
  Group (moved):
    'xa' (modified)
    ': '
    'u32'
  LineBreak('', false)
  ')'
    """.trimIndent())

    fun `test add lifetime`() = doTestChangeSignature("""
        fn foo/*caret*/() {}

        fn bar() {
            foo();
        }
    """, """
        fn foo<'a>(a: &'a str) {}

        fn bar() {
            foo();
        }
    """, "foo", {
        myFixture.type("<'a>")
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT)
        myFixture.type("a: &'a str")
    }, """
Old:
  'foo'
  '('
  LineBreak('', false)
  ')'
New:
  'foo'
  '('
  LineBreak('', true)
  Group (added):
    'a'
    ': '
    '&'a str'
  LineBreak('', false)
  ')'
    """.trimIndent())

    fun `test change method impl`() = doTestChangeSignature("""
        trait Trait {
            fn foo(&self/*caret*/);
        }

        struct S;
        impl Trait for S {
            fn foo(&self) {}
        }
    """, """
        trait Trait {
            fn foo(&self, a: u32);
        }

        struct S;
        impl Trait for S {
            fn foo(&self, a: u32) {}
        }
    """, "foo", {
        myFixture.type(", a: u32")
    }, """
Old:
  'foo'
  '('
  LineBreak('', false)
  ')'
New:
  'foo'
  '('
  LineBreak('', true)
  Group (added):
    'a'
    ': '
    'u32'
  LineBreak('', false)
  ')'
    """.trimIndent())

    fun `test default value only parameter`() {
        val factory = RsPsiFactory(project)
        val exprs = listOf(
            factory.createExpression("42")
        )

        withMockedDefaultValues(exprs) {
            doTestChangeSignature("""
                fn foo(/*caret*/) {}

                fn bar() {
                    foo();
                }
            """, """
                fn foo(a: u32) {}

                fn bar() {
                    foo(42);
                }
            """, "foo", {
                myFixture.type("a: u32")
            }, """
Old:
  'foo'
  '('
  LineBreak('', false)
  ')'
New:
  'foo'
  '('
  LineBreak('', true)
  Group (added):
    'a'
    ': '
    'u32'
  LineBreak('', false)
  ')'
    """.trimIndent())
        }
    }

    fun `test default value multiple parameters`() {
        val factory = RsPsiFactory(project)
        val exprs = listOf(
            factory.createExpression("1"),
            factory.createExpression("3")
        )

        withMockedDefaultValues(exprs) {
            doTestChangeSignature("""
                fn foo(/*caret*/b: u32) {}

                fn bar() {
                    foo(2);
                }
            """, """
                fn foo(a: u32, b: u32, c: u32) {}

                fn bar() {
                    foo(1, 2, 3);
                }
            """, "foo", {
                myFixture.type("a: u32, ")
                repeat(6) {
                    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT)
                }
                myFixture.type(", c: u32")
            }, """
Old:
  'foo'
  '('
  LineBreak('', true)
  Group:
    'b'
    ': '
    'u32'
  LineBreak('', false)
  ')'
New:
  'foo'
  '('
  LineBreak('', true)
  Group (added):
    'a'
    ': '
    'u32'
  ','
  LineBreak(' ', true)
  Group:
    'b'
    ': '
    'u32'
  ','
  LineBreak(' ', true)
  Group (added):
    'c'
    ': '
    'u32'
  LineBreak('', false)
  ')'
    """.trimIndent())
        }
    }

    fun `test change trait method parameter type`() = doTestChangeSignature("""
        trait Trait {
            fn foo(&self, a: /*caret*/u32);
        }
        struct S;
        impl Trait for S {
            fn foo(&self, a: u32) {}
        }
    """, """
        trait Trait {
            fn foo(&self, a: i32);
        }
        struct S;
        impl Trait for S {
            fn foo(&self, a: i32) {}
        }
    """, "foo", {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE)
        myFixture.type("i")
    }, """
Old:
  'foo'
  '('
  LineBreak('', true)
  Group:
    'a'
    ': '
    'u32' (modified)
  LineBreak('', false)
  ')'
New:
  'foo'
  '('
  LineBreak('', true)
  Group:
    'a'
    ': '
    'i32' (modified)
  LineBreak('', false)
  ')'
    """.trimIndent())

    private fun withMockedDefaultValues(expressions: List<RsExpr>, action: () -> Unit) {
        val originalValue = _suggestedChangeSignatureNewParameterValuesForTests
        try {
            _suggestedChangeSignatureNewParameterValuesForTests = {
                SuggestedRefactoringExecution.NewParameterValue.Expression(expressions[it])
            }
            action()
        } finally {
            _suggestedChangeSignatureNewParameterValuesForTests = originalValue
        }
    }
}
