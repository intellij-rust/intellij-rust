/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.ide.intentions.createFromUsage.CreateTupleStructIntention

class CreateTupleStructIntentionTest : RsIntentionTestBase(CreateTupleStructIntention::class) {

    override val previewExpected: Boolean get() = false

    fun `test tuple struct availability range`() = checkAvailableInSelectionOnly("""
        fn main() {
            <selection>Foo</selection>(0);
        }
    """)

    fun `test require camel case`() = doUnavailableTest("""
        fn main() {
            foo/*caret*/(0);
        }
    """)

    fun `test different expected type`() = doUnavailableTest("""
        fn main() {
            let x: u32 = Foo/*caret*/(0);
        }
    """)

    fun `test simple`() = doAvailableTest("""
        fn main() {
            Foo/*caret*/(0);
        }
    """, """
        struct Foo(i32);

        fn main() {
            Foo(0);
        }
    """)

    fun `test no fields`() = doAvailableTest("""
        fn main() {
            Foo/*caret*/();
        }
    """, """
        struct Foo();

        fn main() {
            Foo();
        }
    """)

    fun `test multiple fields`() = doAvailableTest("""
        fn main() {
            Foo/*caret*/(true, "foo");
        }
    """, """
        struct Foo(bool, &'static str);

        fn main() {
            Foo(true, "foo");
        }
    """)

    fun `test unknown types`() = doAvailableTestWithLiveTemplate("""
        fn main() {
            Foo/*caret*/(1, Bar, 2, Baz, (Unknown1, Unknown2));
        }
    """, "Type1\tType2\tType3\tType4\t", """
        struct Foo(i32, Type1, i32, Type2, (Type3, Type4));

        fn main() {
            Foo(1, Bar, 2, Baz, (Unknown1, Unknown2));
        }
    """)

    fun `test create in a module`() = doAvailableTest("""
        mod foo {}

        fn main() {
            foo::Foo/*caret*/(0);
        }
    """, """
        mod foo { pub(crate) struct Foo(pub(crate) i32); }

        fn main() {
            foo::Foo(0);
        }
    """)

    fun `test invoke inside impl`() = doAvailableTest("""
        struct Foo {
            bar: i32,
        }

        impl Foo {
            fn foo() {
                /*caret*/Baz(true);
            }
        }
    """, """
        struct Foo {
            bar: i32,
        }

        struct Baz(bool);

        impl Foo {
            fn foo() {
                Baz(true);
            }
        }
    """)

    fun `test aliased parameter`() = doAvailableTest("""
        type Bar = u32;

        fn foo(bar: Bar) {
            Foo/*caret*/(bar);
        }
    """, """
        type Bar = u32;

        struct Foo(Bar);

        fn foo(bar: Bar) {
            Foo(bar);
        }
    """)

    fun `test type with default type argument`() = doAvailableTest("""
        struct S<T = u32>(T);

        fn foo(bar: S) {
            Foo/*caret*/(bar);
        }
    """, """
        struct S<T = u32>(T);

        struct Foo(S);

        fn foo(bar: S) {
            Foo(bar);
        }
    """)

    fun `test import`() = doAvailableTest("""
        mod bar {
            pub struct S;
        }

        fn foo() {
            Foo/*caret*/(bar::S);
        }
    """, """
        use crate::bar::S;

        mod bar {
            pub struct S;
        }

        struct Foo(S);

        fn foo() {
            Foo(bar::S);
        }
    """)
}
