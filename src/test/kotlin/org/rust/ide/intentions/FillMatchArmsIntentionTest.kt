/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class FillMatchArmsIntentionTest : RsIntentionTestBase(FillMatchArmsIntention()) {

    fun `test simple enum variants`() = doAvailableTest("""
        enum FooBar {
            Foo,
            Bar
        }

        fn foo(x: FooBar) {
            match x/*caret*/ {}
        }
    """, """
        enum FooBar {
            Foo,
            Bar
        }

        fn foo(x: FooBar) {
            match x {
                FooBar::Foo => {/*caret*/},
                FooBar::Bar => {},
            }
        }
    """)

    fun `test tuple enum variants`() = doAvailableTest("""
        enum FooBar {
            Foo(i32),
            Bar(bool, f64)
        }

        fn foo(x: FooBar) {
            match x/*caret*/ {}
        }
    """, """
        enum FooBar {
            Foo(i32),
            Bar(bool, f64)
        }

        fn foo(x: FooBar) {
            match x {
                FooBar::Foo(_) => {/*caret*/},
                FooBar::Bar(_, _) => {},
            }
        }
    """)

    fun `test struct enum variants`() = doAvailableTest("""
        enum FooBar {
            Foo { foo: i32 },
            Bar { bar1: bool, bar2: f64 }
        }

        fn foo(x: FooBar) {
            match x/*caret*/ {}
        }
    """, """
        enum FooBar {
            Foo { foo: i32 },
            Bar { bar1: bool, bar2: f64 }
        }

        fn foo(x: FooBar) {
            match x {
                FooBar::Foo { .. } => {/*caret*/},
                FooBar::Bar { .. } => {},
            }
        }
    """)

    fun `test different enum variants`() = doAvailableTest("""
        enum Foo {
            X,
            Y(i32),
            Z { foo: bool }
        }

        fn foo(x: Foo) {
            match x/*caret*/ {}
        }
    """, """
        enum Foo {
            X,
            Y(i32),
            Z { foo: bool }
        }

        fn foo(x: Foo) {
            match x {
                Foo::X => {/*caret*/},
                Foo::Y(_) => {},
                Foo::Z { .. } => {},
            }
        }
    """)

    fun `test incomplete match expr`() = doAvailableTest("""
        enum FooBar {
            Foo,
            Bar
        }

        fn foo(x: FooBar) {
            match x/*caret*/
        }
    """, """
        enum FooBar {
            Foo,
            Bar
        }

        fn foo(x: FooBar) {
            match x {
                FooBar::Foo => {/*caret*/},
                FooBar::Bar => {},
            }
        }
    """)

    fun `test don't remove comments`() = doAvailableTest("""
        enum FooBar {
            Foo,
            Bar
        }

        fn foo(x: FooBar) {
            match x/*caret*/ {
                // test
            }
        }
    """, """
        enum FooBar {
            Foo,
            Bar
        }

        fn foo(x: FooBar) {
            match x {
                // test
                FooBar::Foo => {/*caret*/},
                FooBar::Bar => {},
            }
        }
    """)

    fun `test not empty match expr body`() = doUnavailableTest("""
        enum FooBar {
            Foo,
            Bar
        }

        fn foo(x: FooBar) {
            match x/*caret*/ {
                FooBar::Foo => {},
            }
        }
    """)

    fun `test not enum in match expr`() = doUnavailableTest("""
        fn foo(x: i32) {
            match x/*caret*/ {}
        }
    """)
}
