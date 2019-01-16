/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */
package org.rust.ide.refactoring

import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsFunction

class RsIntroduceParameterTest : RsTestBase() {
    fun `test method no params`() = doTest("""
        fn hello() {
            foo(5 + /*caret*/10);
        }
    """, listOf("10", "5 + 10", "foo(5 + 10)"), 0, 0, """
        fn hello(/*caret*/i: i32) {
            foo(5 + i);
        }
    """)

    fun `test method with params`() = doTest("""
        fn hello(param: i32) {
            let result = param + /*caret*/10;
        }
    """, listOf("10", "param + 10"), 0, 0, """
        fn hello(param: i32, /*caret*/i: i32) {
            let result = param + i;
        }
    """)

    fun `test inner method chosen`() = doTest("""
        fn outer() {
            fn inner() {
                let k = /*caret*/5.0 + 2.5;
            }
        }
    """, listOf("5.0", "5.0 + 2.5"), 0, 0, """
        fn outer() {
            fn inner(/*caret*/x: f64) {
                let k = x + 2.5;
            }
        }
    """)

    fun `test outer method chosen`() = doTest("""
        fn outer() {
            fn inner() {
                let k = 5.0 + /*caret*/2.5;
            }
        }
    """, listOf("2.5", "5.0 + 2.5"), 0, 1, """
        fn outer(/*caret*/x: f64) {
            fn inner() {
                let k = 5.0 + x;
            }
        }
    """)

    fun `test replace all occurrences`() = doTest("""
        fn hello() {
            let a = /*caret*/5 + 10;
            let b = 5 + 5 + 5;
        }
    """, listOf("5", "5 + 10"), 0, 0, """
        fn hello(/*caret*/i: i32) {
            let a = i + 10;
            let b = i + i + i;
        }
    """, replaceAll = true)

    fun `test replace current occurrence only`() = doTest("""
        fn hello() {
            let a = 5 + 10;
            let b = 5 + /*caret*/5 + 5;
        }
    """, listOf("5", "5 + 5", "5 + 5 + 5"), 0, 0, """
        fn hello(/*caret*/i: i32) {
            let a = 5 + 10;
            let b = 5 + i + 5;
        }
    """, replaceAll = false)

    fun `test method usages modified`() = doTest("""
        fn test() {
            fn hello() {
                5 + /*caret*/10;
            }
            hello();
        }
    """, listOf("10", "5 + 10"), 0, 0, """
        fn test() {
            fn hello(/*caret*/i: i32) {
                5 + i;
            }
            hello(10);
        }
    """)

    fun `test method usages modified when complicated call`() = doTest("""
        fn test() {
            fn hello(a: i32) {
                5 + /*caret*/10;
            }
            (hello)(5);
        }
    """, listOf("10", "5 + 10"), 0, 0, """
        fn test() {
            fn hello(a: i32, /*caret*/i: i32) {
                5 + i;
            }
            (hello)(5, 10);
        }
    """)

    fun `test method usages modified for method params`() = doTest("""
        fn test() {
            fn hello(k: i32) {
                k + /*caret*/10;
            }
            hello(42);
        }
    """, listOf("10", "k + 10"), 0, 0, """
        fn test() {
            fn hello(k: i32, /*caret*/i: i32) {
                k + i;
            }
            hello(42, 10);
        }
    """)

    fun `test call method with self`() = doTest("""
        struct Camel { name: &'static str }
         impl Camel {
            fn drink(&self) {
                let some_val = 1 + 2/*caret*/;
            }
            fn test(&self) {
                self.drink();
            }
        }
    """, listOf("2", "1 + 2"), 0, 0, """
        struct Camel { name: &'static str }
         impl Camel {
            fn drink(&self, /*caret*/i: i32) {
                let some_val = 1 + i;
            }
            fn test(&self) {
                self.drink(2);
            }
        }
    """)

    fun `test change signature of trait impl`() = doTest("""
        struct Camel { name: &'static str }
         trait Animal {
            fn run(&self);
         }
         impl Animal for Camel {
            fn run(&self) {
                let c = /*caret*/5 + 6;
            }
        }
    """, listOf("5", "5 + 6"), 0, 0, """
        struct Camel { name: &'static str }
         trait Animal {
            fn run(&self, i: i32);
         }
         impl Animal for Camel {
            fn run(&self, /*caret*/i: i32) {
                let c = i + 6;
            }
        }
    """)

    fun `test change signature of trait with two impls`() = doTest("""
        struct Camel { name: &'static str }
        struct Hare { name: &'static str }
        trait Animal {
            fn run(&self);
        }
        impl Animal for Camel {
            fn run(&self) {
                let c = /*caret*/5 + 6;
            }
        }
        impl Animal for Hare {
            fn run(&self) { }
        }
    """, listOf("5", "5 + 6"), 0, 0, """
        struct Camel { name: &'static str }
        struct Hare { name: &'static str }
        trait Animal {
            fn run(&self, i: i32);
        }
        impl Animal for Camel {
            fn run(&self, /*caret*/i: i32) {
                let c = i + 6;
            }
        }
        impl Animal for Hare {
            fn run(&self, i: i32) { }
        }
    """)

    fun `test usages change when change signature of trait`() = doTest("""
        struct Camel { name: &'static str }
         trait Animal {
            fn run(&self);
             fn action(&self) { }
        }
         impl Animal for Camel {
            fn run(&self) {
                let c = /*caret*/5 + 6;
            }
            fn action(&self) {
                self.run();
            }
        }
    """, listOf("5", "5 + 6"), 0, 0, """
        struct Camel { name: &'static str }
         trait Animal {
            fn run(&self, i: i32);
             fn action(&self) { }
        }
         impl Animal for Camel {
            fn run(&self, /*caret*/i: i32) {
                let c = i + 6;
            }
            fn action(&self) {
                self.run(5);
            }
        }
    """)

    fun `test change trait method`() = doTest("""
        struct Camel { name: &'static str }
         trait Animal {
            fn run(&self) {
                let c = /*caret*/5 + 6;
            }
        }
        impl Animal for Camel {
            fn run(&self) {
                let c = 12;
            }
        }
    """, listOf("5", "5 + 6"), 0, 0, """
        struct Camel { name: &'static str }
         trait Animal {
            fn run(&self, /*caret*/i: i32) {
                let c = i + 6;
            }
        }
        impl Animal for Camel {
            fn run(&self, i: i32) {
                let c = 12;
            }
        }
    """)

    private fun doTest(
        @Language("Rust") before: String,
        expressions: List<String>,
        exprTarget: Int,
        methodTarget: Int,
        @Language("Rust") after: String,
        replaceAll: Boolean = false
    ) {
        checkByText(before, after) {
            doIntroduce(expressions, exprTarget, methodTarget, replaceAll)
        }
    }

    private fun doIntroduce(expressions: List<String>,
                            exprTarget: Int,
                            methodTarget: Int,
                            replaceAll: Boolean) {
        var shownTargetChooser = false
        withMockTargetExpressionChooser(object : ExtractExpressionUi {
            override fun chooseTarget(exprs: List<RsExpr>): RsExpr {
                shownTargetChooser = true
                TestCase.assertEquals(exprs.map { it.text }, expressions)
                return exprs[exprTarget]
            }

            override fun chooseOccurrences(expr: RsExpr, occurrences: List<RsExpr>): List<RsExpr> =
                if (replaceAll) occurrences else listOf(expr)

            override fun chooseMethod(methods: List<RsFunction>): RsFunction {
                return methods[methodTarget]
            }
        }) {
            myFixture.performEditorAction("IntroduceParameter")
            if (expressions.isNotEmpty() && !shownTargetChooser) {
                error("Didn't shown chooser")
            }
        }
    }
}
