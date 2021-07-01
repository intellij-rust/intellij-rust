/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.ide.intentions.createFromUsage.CreateStructIntention

class CreateStructIntentionTest : RsIntentionTestBase(CreateStructIntention::class) {
    fun `test struct availability range`() = checkAvailableInSelectionOnly("""
        fn main() {
            <selection>Foo</selection> { a: 0 };
        }
    """)

    fun `test simple`() = doAvailableTest("""
        fn main() {
            Foo/*caret*/ { a: 0 };
        }
    """, """
        struct Foo {
            a: i32
        }

        fn main() {
            Foo { a: 0 };
        }
    """)

    fun `test shorthand field`() = doAvailableTest("""
        fn main() {
            let a = 1;
            Foo/*caret*/ { a };
        }
    """, """
        struct Foo {
            a: i32
        }

        fn main() {
            let a = 1;
            Foo { a };
        }
    """)

    fun `test no fields`() = doAvailableTest("""
        fn main() {
            Foo/*caret*/ {};
        }
    """, """
        struct Foo;

        fn main() {
            Foo {};
        }
    """)

    fun `test multiple fields`() = doAvailableTest("""
        fn main() {
            Foo/*caret*/ { a: true, b: "foo" };
        }
    """, """
        struct Foo {
            a: bool,
            b: &'static str
        }

        fn main() {
            Foo { a: true, b: "foo" };
        }
    """)

    fun `test unknown type`() = doAvailableTest("""
        fn main() {
            Foo/*caret*/ { a: Bar };
        }
    """, """
        struct Foo {
            a: _
        }

        fn main() {
            Foo { a: Bar };
        }
    """)

    fun `test create in a module`() = doAvailableTest("""
        mod foo {}

        fn main() {
            foo::Foo/*caret*/ { a: 0 };
        }
    """, """
        mod foo {
            pub(crate) struct Foo {
                pub(crate) a: i32
            }
        }

        fn main() {
            foo::Foo { a: 0 };
        }
    """)

    fun `test invoke inside impl`() = doAvailableTest("""
        struct Foo {
            bar: i32,
        }

        impl Foo {
            fn foo() {
                /*caret*/Baz { a: true }
            }
        }
    """, """
        struct Foo {
            bar: i32,
        }

        struct Baz {
            a: bool
        }

        impl Foo {
            fn foo() {
                Baz { a: true }
            }
        }
    """)
}
