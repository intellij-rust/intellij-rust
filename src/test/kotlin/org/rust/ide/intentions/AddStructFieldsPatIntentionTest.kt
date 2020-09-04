/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class AddStructFieldsPatIntentionTest : RsIntentionTestBase(AddStructFieldsPatIntention::class) {
    fun `test simple case match`() = doAvailableTest("""
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn f(foo: Foo) {
            match foo {
                Foo { a, b, ./*caret*/. } => {}
            }
        }
    """, """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn f(foo: Foo) {
            match foo {
                Foo { a, b, c, d /*caret*/} => {}
            }
        }
    """)

    fun `test no fields match`() = doAvailableTest("""
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn f(foo: Foo) {
            match foo {
                Foo { ./*caret*/. } => {}
            }
        }
    """, """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn f(foo: Foo) {
            match foo {
                Foo { a, b, c, d /*caret*/} => {}
            }
        }
    """)

    fun `test simple let expression`() = doAvailableTest("""
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn f(foo: Foo) {
            let Foo { a, b, ./*caret*/. } = foo;
        }
    """, """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn f(foo: Foo) {
            let Foo { a, b, c, d /*caret*/} = foo;
        }
    """)

    fun `test no fields let expression`() = doAvailableTest("""
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn f(foo: Foo) {
            let Foo { ./*caret*/. } = foo;
        }
    """, """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn f(foo: Foo) {
            let Foo { a, b, c, d /*caret*/} = foo;
        }
    """)

    fun `test match one field missing`() = doAvailableTest("""
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn f(foo: Foo) {
            match foo {
                Foo { a, b, c, ./*caret*/. } => {}
            }
        }
    """, """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn f(foo: Foo) {
            match foo {
                Foo { a, b, c, d /*caret*/} => {}
            }
        }
    """)

    fun `test let one field missing`() = doAvailableTest("""
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn f(foo: Foo) {
            let Foo { a, b, c, ./*caret*/. } = foo;
        }
    """, """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn f(foo: Foo) {
            let Foo { a, b, c, d /*caret*/} = foo;
        }
    """)

    fun `test filling order with existing 2 last fields in descending order`() = doAvailableTest("""
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn f(foo: Foo) {
            let Foo { d, c, ./*caret*/. } = foo;
        }
    """, """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn f(foo: Foo) {
            let Foo { a, b, d, c /*caret*/} = foo;
        }
    """)

    fun `test filling order with the second field existing`() = doAvailableTest("""
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn f(foo: Foo) {
            let Foo { b, ./*caret*/. } = foo;
        }
    """, """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn f(foo: Foo) {
            let Foo { a, b, c, d /*caret*/} = foo;
        }
    """)

    fun `test tuple struct with dots`() = doAvailableTest("""
        struct Foo(i32, i32, i32);
        fn f(foo: Foo) {
            match foo {
                Foo (a, b, ./*caret*/.) => {}
            }
        }
    """, """
        struct Foo(i32, i32, i32);
        fn f(foo: Foo) {
            match foo {
                Foo (a, b, _0/*caret*/) => {}
            }
        }
    """)

    fun `test struct with no spaces`() = doAvailableTest("""
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
        }

        fn f(foo: Foo) {
            let Foo {a,b,./*caret*/.} = foo;
        }
    """, """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
        }

        fn f(foo: Foo) {
            let Foo {a,b,/*caret*/ c } = foo;
        }
    """)

    fun `test tuple struct with no spaces`() = doAvailableTest("""
        struct Foo(i32, i32, i32);
        fn f(foo: Foo) {
            match foo {
                Foo (a,b,./*caret*/.) => {}
            }
        }
    """, """
        struct Foo(i32, i32, i32);
        fn f(foo: Foo) {
            match foo {
                Foo (a, b, _0/*caret*/) => {}
            }
        }
    """)

    fun `test intention not available in range`() = doUnavailableTest("""
        fn f() {
            match 1 {
                0./*caret*/.2 => {}
                _ => {}
            }
        }
    """)

    fun `test tuple with dots in the middle`() = doAvailableTest("""
        struct Foo(i32, i32, i32);

        fn f(foo: Foo) {
            let Foo (a, ./*caret*/., c) = foo;
        }
    """, """
        struct Foo(i32, i32, i32);

        fn f(foo: Foo) {
            let Foo (a, _0/*caret*/, c) = foo;
        }
    """)

    fun `test tuple with dots in the middle and trailing comma`() = doAvailableTest("""
        struct Foo(i32, i32, i32);

        fn f(foo: Foo) {
            let Foo (a, ./*caret*/., c,) = foo;
        }
    """, """
        struct Foo(i32, i32, i32);

        fn f(foo: Foo) {
            let Foo (a, _0/*caret*/, c,) = foo;
        }
    """)

    fun `test tuple with dots in the middle and 4 fields`() = doAvailableTest("""
        struct Foo(i32, i32, i32, i32);

        fn f(foo: Foo) {
            let Foo (a, ./*caret*/., c) = foo;
        }
    """, """
        struct Foo(i32, i32, i32, i32);

        fn f(foo: Foo) {
            let Foo (a, _0/*caret*/, _1, c) = foo;
        }
    """)

    fun `test tuple with dots in the middle and 5 fields`() = doAvailableTest("""
        struct Foo(i32, i32, i32, i32, i32);

        fn f(foo: Foo) {
            let Foo (a, ./*caret*/., c) = foo;
        }
    """, """
        struct Foo(i32, i32, i32, i32, i32);

        fn f(foo: Foo) {
            let Foo (a, _0/*caret*/, _1, _2, c) = foo;
        }
    """)
}
