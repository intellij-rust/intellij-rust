/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

import org.rust.ide.typing.RsTypingTestBase

class RsAutoIndentTest : RsTypingTestBase() {
    fun `test fn`() = doTestByText("""
        fn main() {/*caret*/
    """, """
        fn main() {
            /*caret*/
        }
    """)

    fun `test if`() = doTestByText("""
        fn main() {
            if (true) {/*caret*/
        }
    """, """
        fn main() {
            if (true) {
                /*caret*/
            }
        }
    """)

    fun `test mod item`() = doTestByText("""
        mod foo {/*caret*/
    """, """
        mod foo {
            /*caret*/
        }
    """)

    fun `test mod item 2`() = doTestByText("""
        mod/*caret*/
        foo {}
    """, """
        mod
        /*caret*/
        foo {}
    """)

    fun `test foreign mod item`() = doTestByText("""
        extern {/*caret*/
    """, """
        extern {
            /*caret*/
        }
    """)

    fun `test foreign mod item 2`() = doTestByText("""
        extern/*caret*/ {}
    """, """
        extern
        /*caret*/{}
    """)

    fun `test pat`() = doTestByText("""
        fn main() {
            match 1 {
                Foo {/*caret*/
            }
        }
    """, """
        fn main() {
            match 1 {
                Foo {
                    /*caret*/
                }
            }
        }
    """)

    fun `test chain call`() = doTestByText("""
        fn main() {
            let moo = foo().bar().baz()/*caret*/
        }
    """, """
        fn main() {
            let moo = foo().bar().baz()
            /*caret*/
        }
    """)

    fun `test chain call 2`() = doTestByText("""
        fn main() {
            let moo = foo().bar()/*caret*/.baz()
        }
    """, """
        fn main() {
            let moo = foo().bar()
                /*caret*/.baz()
        }
    """)

    fun `test chain call 3`() = doTestByText("""
        fn main() {
            let moo = foo().bar().baz()
                           .moo()/*caret*/
        }
    """, """
        fn main() {
            let moo = foo().bar().baz()
                           .moo()
            /*caret*/
        }
    """)

    fun `test chain call 4`() = doTestByText("""
        fn main() {
            let moo = foo().bar().baz()
                           .moo()/*caret*/;
        }
    """, """
        fn main() {
            let moo = foo().bar().baz()
                           .moo()
            /*caret*/;
        }
    """)

    fun `test chain call 5`() = doTestByText("""
        fn main() {
            moo()/*caret*/
                .boo()
                .goo()
        }
    """, """
        fn main() {
            moo()
                /*caret*/
                .boo()
                .goo()
        }
    """)

    fun `test expr`() = doTestByText("""
        fn main() {
            if foo && bar/*caret*/
                && foo {}
        }
    """, """
        fn main() {
            if foo && bar
                /*caret*/
                && foo {}
        }
    """)

    fun `test expr 2`() = doTestByText("""
        fn main() {
            foo && bar/*caret*/
                || boo
        }
    """, """
        fn main() {
            foo && bar
                /*caret*/
                || boo
        }
    """)

    fun `test expr 3`() = doTestByText("""
        fn main() {
            foo && bar ||/*caret*/
        }
    """, """
        fn main() {
            foo && bar ||
                /*caret*/
        }
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/475
    fun `test issue 475`() = doTestByText("""
        fn foobar(n: u64) -> String {
            match (n % 3, n % 5) {
                /*caret*/
            }
        }
    """, """
        fn foobar(n: u64) -> String {
            match (n % 3, n % 5) {
                (/*caret*/)
            }
        }
    """, '(')

    fun `test function parameter`() = doTestByText("""
        fn foo(xs: i32,/*caret*/
               ys: i32) {}
    """, """
        fn foo(xs: i32,
               /*caret*/
               ys: i32) {}
    """)

    fun `test first function parameter`() = doTestByText("""
        fn foo(/*caret*/) {}
    """, """
        fn foo(
            /*caret*/
        ) {}
    """)

    fun `test first call last argument without comma`() = doTestByText("""
        fn main() {
            frobnicate(
                xs/*caret*/)
        }
    """, """
        fn main() {
            frobnicate(
                xs
            /*caret*/)
        }
    """)

    // FIXME
    // Ideally, we want to indent this because of the trailing comma.
    // This might require a custom enter processor though, because
    // if there's non-ws after the caret (`)` in this case), autoindent
    // skips [org.rust.ide.formatter.blocks.RsFmtBlock.getChildAttributes]
    // and just reformats everything.
    fun `test first call last argument with comma`() = doTestByText("""
        fn main() {
            frobnicate(
                xs,/*caret*/)
        }
    """, """
        fn main() {
            frobnicate(
                xs,
            /*caret*/)
        }
    """)

    fun `test new struct field`() = doTestByText("""
        struct ManifestWarning {
            message: String,/*caret*/
        }
    """, """
        struct ManifestWarning {
            message: String,
            /*caret*/
        }
    """)

    fun `test first struct field`() = doTestByText("""
        struct ManifestWarning {/*caret*/
        }
    """, """
        struct ManifestWarning {
            /*caret*/
        }
    """)
}
