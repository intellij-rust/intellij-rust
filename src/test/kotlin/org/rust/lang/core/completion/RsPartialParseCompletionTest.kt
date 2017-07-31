/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsPartialParseCompletionTest : RsCompletionTestBase() {
    fun testMatch() = checkSingleCompletion("tokenizer", """
            pub fn parse(tokenizer: lexer::Tokenizer) -> ast::Expr {
                match tok/*caret*/
            }
    """)

    fun testIfLet() = checkSingleCompletion("tokenizer", """
        pub fn parse(tokenizer: lexer::Tokenizer) -> ast::Expr {
            if let Some(_) = tok/*caret*/
        }
    """)

    fun testWhileLet() = checkSingleCompletion("numbers", """
        fn main() {
            let numbers = vec![1, 2, 3].iter();
            while let Some(_) = num/*caret*/
        }
    """)

    fun testIf() = checkSingleCompletion("quuz", """
        fn foo(quuz: bool) {
            if qu/*caret*/
        }
    """)

    fun testWhile() = checkSingleCompletion("condition", """
        fn foo() {
            let condition: bool = true;

            while cond/*caret*/
        }
    """)

    fun testTypeParams() = checkSingleCompletion("Walrus", """
        struct Walrus {
            stomach: Vec<()>
        }

        fn make_walrus() -> Result<(), Wal/*caret*/
    """)

    fun testImpl() = checkSingleCompletion("AutomatonTrait", """
        trait AutomatonTrait { }

        impl Auto/*caret*/
    """)

    fun testImpl2() = checkSingleCompletion("AutomatonStruct", """
        struct AutomatonStruct { }

        impl Auto/*caret*/
    """)

    fun testImpl3() = checkSingleCompletion("FooBar", """
        trait Automaton { }

        struct FooBar;

        impl Automaton for Foo/*caret*/
    """)

    fun testLet() = checkSingleCompletion("Spam", """
        struct Spam;

        fn main() {
            let x = Sp/*caret*/
            ()
        }
    """)

    fun testImplMethodType() = checkSingleCompletion("FooBar", """
        pub struct FooBar;

        struct S;

        trait T {
            fn foo(self, f: &mut FooBar);
        }

        impl T for S {
            fn foo(self, f: Fo/*caret*/)
        }
    """)

    fun testStructField() = checkSingleCompletion("foobar", """
        struct S {
            foobar: i32,
            frobnicator: i32,
        }

        fn main() {
            let _ = S {
                foo/*caret*/
                frobnicator: 92
            };
        }
    """)

    fun testStatic() = checkSingleCompletion("FooBar", """
        struct FooBar;

        static C: Foo/*caret*/
    """)

    fun testConst() = checkSingleCompletion("FooBar", """
        struct FooBar;

        const C: Foo/*caret*/
    """)

    fun testUseGlobs() = checkSingleCompletion("quux", """
        use self::m::{foo, qu/*caret*/ bar};

        mod m {
            fn foo() {}
            fn bar() {}
            fn quux() {}
        }
    """)

    fun testTupleStruct() = checkSingleCompletion("FooBar", """
        type FooBar = ();
        struct S(Fo/*caret*/)
    """)

    fun `test statement recovery`() = checkByText("""
        fn ubik() { }

        fn main() {
            let _ =

            let _ = ubi/*caret*/
        }
    """, """
        fn ubik() { }

        fn main() {
            let _ =

            let _ = ubik()
        }
    """) {
        executeSoloCompletion()
    }

    fun `test struct field`() = checkSingleCompletion("bar", """
        struct S { foo: i32, bar: i32}
        fn main() { let _ = S { foo: 2, .ba/*caret*/ } }
    """)

    fun `test function parameter`() = doSingleCompletion("""
        struct Frobnicate;
        fn foo(x: i32, foo bar: i32, baz: Frob/*caret*/) {}
    """, """
        struct Frobnicate;
        fn foo(x: i32, foo bar: i32, baz: Frobnicate/*caret*/) {}
    """)
}

