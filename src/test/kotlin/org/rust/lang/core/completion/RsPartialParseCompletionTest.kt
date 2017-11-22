/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsPartialParseCompletionTest : RsCompletionTestBase() {
    fun `test match`() = @Suppress("DEPRECATION") checkSingleCompletion("tokenizer", """
            pub fn parse(tokenizer: lexer::Tokenizer) -> ast::Expr {
                match tok/*caret*/
            }
    """)

    fun `test if let`() = @Suppress("DEPRECATION") checkSingleCompletion("tokenizer", """
        pub fn parse(tokenizer: lexer::Tokenizer) -> ast::Expr {
            if let Some(_) = tok/*caret*/
        }
    """)

    fun `test while let`() = @Suppress("DEPRECATION") checkSingleCompletion("numbers", """
        fn main() {
            let numbers = vec![1, 2, 3].iter();
            while let Some(_) = num/*caret*/
        }
    """)

    fun `test if`() = @Suppress("DEPRECATION") checkSingleCompletion("quuz", """
        fn foo(quuz: bool) {
            if qu/*caret*/
        }
    """)

    fun `test while`() = @Suppress("DEPRECATION") checkSingleCompletion("condition", """
        fn foo() {
            let condition: bool = true;

            while cond/*caret*/
        }
    """)

    fun `test type params`() = @Suppress("DEPRECATION") checkSingleCompletion("Walrus", """
        struct Walrus {
            stomach: Vec<()>
        }

        fn make_walrus() -> Result<(), Wal/*caret*/
    """)

    fun `test impl`() = @Suppress("DEPRECATION") checkSingleCompletion("AutomatonTrait", """
        trait AutomatonTrait { }

        impl Auto/*caret*/
    """)

    fun `test impl 2`() = @Suppress("DEPRECATION") checkSingleCompletion("AutomatonStruct", """
        struct AutomatonStruct { }

        impl Auto/*caret*/
    """)

    fun `test impl 3`() = @Suppress("DEPRECATION") checkSingleCompletion("FooBar", """
        trait Automaton { }

        struct FooBar;

        impl Automaton for Foo/*caret*/
    """)

    fun `test let`() = @Suppress("DEPRECATION") checkSingleCompletion("Spam", """
        struct Spam;

        fn main() {
            let x = Sp/*caret*/
            ()
        }
    """)

    fun `test impl method type`() = @Suppress("DEPRECATION") checkSingleCompletion("FooBar", """
        pub struct FooBar;

        struct S;

        trait T {
            fn foo(self, f: &mut FooBar);
        }

        impl T for S {
            fn foo(self, f: Fo/*caret*/)
        }
    """)

    fun `test struct field 1`() = @Suppress("DEPRECATION") checkSingleCompletion("foobar", """
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

    fun `test static`() = @Suppress("DEPRECATION") checkSingleCompletion("FooBar", """
        struct FooBar;

        static C: Foo/*caret*/
    """)

    fun `test const`() = @Suppress("DEPRECATION") checkSingleCompletion("FooBar", """
        struct FooBar;

        const C: Foo/*caret*/
    """)

    fun `test use globs`() = @Suppress("DEPRECATION") checkSingleCompletion("quux", """
        use self::m::{foo, qu/*caret*/ bar};

        mod m {
            fn foo() {}
            fn bar() {}
            fn quux() {}
        }
    """)

    fun `test tuple struct`() = @Suppress("DEPRECATION") checkSingleCompletion("FooBar", """
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

    fun `test struct field 2`() = @Suppress("DEPRECATION") checkSingleCompletion("bar", """
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

