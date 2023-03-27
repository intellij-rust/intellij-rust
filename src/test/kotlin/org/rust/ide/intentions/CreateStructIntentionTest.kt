/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.ide.intentions.createFromUsage.CreateStructIntention

class CreateStructIntentionTest : RsIntentionTestBase(CreateStructIntention::class) {

    override val previewExpected: Boolean get() = false

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
        struct Foo {}

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

    fun `test unknown types`() = doAvailableTestWithLiveTemplate("""
        fn main() {
            Foo/*caret*/ { x1: Bar, x2: (Unknown1, Unknown2) };
        }
    """, "x1\tType1\tx2\tType2\tType3\t", """
        struct Foo {
            x1: Type1,
            x2: (Type2, Type3)
        }

        fn main() {
            Foo { x1: Bar, x2: (Unknown1, Unknown2) };
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

    fun `test field with aliased type`() = doAvailableTest("""
        type Bar = u32;

        fn foo(bar: Bar) {
            Foo/*caret*/ { a: bar };
        }
    """, """
        type Bar = u32;

        struct Foo {
            a: Bar
        }

        fn foo(bar: Bar) {
            Foo { a: bar };
        }
    """)

    fun `test field with default type argument`() = doAvailableTest("""
        struct S<T = u32>(T);

        fn foo(bar: S<u32>) {
            Foo/*caret*/ { a: bar };
        }
    """, """
        struct S<T = u32>(T);

        struct Foo {
            a: S
        }

        fn foo(bar: S<u32>) {
            Foo { a: bar };
        }
    """)

    fun `test import type`() = doAvailableTest("""
        mod bar {
            pub struct S;
        }

        fn foo() {
            Foo/*caret*/ { a: bar::S };
        }
    """, """
        use crate::bar::S;

        mod bar {
            pub struct S;
        }

        struct Foo {
            a: S
        }

        fn foo() {
            Foo { a: bar::S };
        }
    """)
}
