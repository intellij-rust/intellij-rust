/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class AddStructFieldsLiteralIntentionTest : RsIntentionTestBase(AddStructFieldsLiteralIntention::class) {
    fun `test smoke struct literal`() = doAvailableTest("""
        struct Foo { bar: i32, baz: i32 }

        fn f() {
            let foo = Foo { bar: 0, baz: 1 };
            let bar = Foo { bar: 2, ./*caret*/.foo };
        }
    """, """
        struct Foo { bar: i32, baz: i32 }

        fn f() {
            let foo = Foo { bar: 0, baz: 1 };
            let bar = Foo { bar: 2, baz: 0/*caret*/ };
        }
    """)

    fun `test smoke struct literal with space after dots`() = doAvailableTest("""
        struct Foo { bar: i32, baz: i32 }

        fn f() {
            let foo = Foo { bar: 0, baz: 1 };
            let bar = Foo { bar: 2, ./*caret*/. foo };
        }
    """, """
        struct Foo { bar: i32, baz: i32 }

        fn f() {
            let foo = Foo { bar: 0, baz: 1 };
            let bar = Foo { bar: 2, baz: 0/*caret*/ };
        }
    """)

    fun `test struct literal with comment after dots`() = doAvailableTest("""
        struct Foo { bar: i32, baz: i32 }

        fn f() {
            let foo = Foo { bar: 0, baz: 1 };
            let bar = Foo { bar: 2, ./*caret*/./*comment*/foo };
        }
    """, """
        struct Foo { bar: i32, baz: i32 }

        fn f() {
            let foo = Foo { bar: 0, baz: 1 };
            let bar = Foo { bar: 2, /*comment*/ baz: 0/*caret*/ };
        }
    """)

    fun `test struct literal with inner struct`() = doAvailableTest("""
        struct Foo { bar: i32, baz: i32, quux: Bar }

        struct Bar { spam: i32 }

        fn f() {
            let foo = Foo { bar: 0, baz: 1, quux: Bar { spam: 2 } };
            let bar = Foo { bar: 2, ./*caret*/.foo};
        }
    """, """
        struct Foo { bar: i32, baz: i32, quux: Bar }

        struct Bar { spam: i32 }

        fn f() {
            let foo = Foo { bar: 0, baz: 1, quux: Bar { spam: 2 } };
            let bar = Foo { bar: 2, baz: 0/*caret*/, quux: Bar {} };
        }
    """)

    fun `test struct literal with inner tuple struct`() = doAvailableTest("""
        struct Foo { bar: i32, baz: i32, quux: Bar }

        struct Bar(i32);

        fn f() {
            let foo = Foo { bar: 0, baz: 1, quux: Bar(2) };
            let bar = Foo { bar: 2, ./*caret*/.foo};
        }
    """, """
        struct Foo { bar: i32, baz: i32, quux: Bar }

        struct Bar(i32);

        fn f() {
            let foo = Foo { bar: 0, baz: 1, quux: Bar(2) };
            let bar = Foo { bar: 2, baz: 0/*caret*/, quux: Bar() };
        }
    """)

    fun `test struct literal with second field`() = doAvailableTest("""
        struct Foo { bar: i32, baz: i32 }

        fn f() {
            let foo = Foo { bar: 0, baz: 1 };
            let bar = Foo { baz: 2, ./*caret*/.foo };
        }
    """, """
        struct Foo { bar: i32, baz: i32 }

        fn f() {
            let foo = Foo { bar: 0, baz: 1 };
            let bar = Foo { bar: 0/*caret*/, baz: 2, };
        }
    """)

    fun `test struct literal with second field in the middle`() = doAvailableTest("""
        struct Foo { bar: i32, baz: i32, quux: i32 }

        fn f() {
            let foo = Foo { bar: 0, baz: 1, quux: 2 };
            let bar = Foo { baz: 2, ./*caret*/.foo };
        }
    """, """
        struct Foo { bar: i32, baz: i32, quux: i32 }

        fn f() {
            let foo = Foo { bar: 0, baz: 1, quux: 2 };
            let bar = Foo { bar: 0/*caret*/, baz: 2, quux: 0 };
        }
    """)

    fun `test struct literal with non existent field`() = doAvailableTest("""
        struct Foo { bar: i32, baz: i32, quux: i32 }

        fn f() {
            let bar = Foo { baz: 2, ./*caret*/.foo };
        }
    """, """
        struct Foo { bar: i32, baz: i32, quux: i32 }

        fn f() {
            let bar = Foo { bar: 0/*caret*/, baz: 2, quux: 0 };
        }
    """)

    fun `test struct literal with no fields`() = doAvailableTest("""
        struct Foo { bar: i32, baz: i32 }

        fn f() {
            let bar = Foo { ./*caret*/.foo };
        }
    """, """
        struct Foo { bar: i32, baz: i32 }

        fn f() {
            let bar = Foo { bar: 0/*caret*/, baz: 0 };
        }
    """)

    fun `test tuple struct literal`() = doAvailableTest("""
        struct Foo(i32, i32);

        fn f() {
            let foo = Foo { 0: 0, 1: 1 };
            let bar = Foo { ./*caret*/.foo };
        }
    """, """
        struct Foo(i32, i32);

        fn f() {
            let foo = Foo { 0: 0, 1: 1 };
            let bar = Foo { 0: 0/*caret*/, 1: 0 };
        }
    """)

    fun `test tuple struct literal with second field`() = doAvailableTest("""
        struct Foo(i32, i32);

        fn f() {
            let foo = Foo { 0: 0, 1: 1 };
            let bar = Foo { 1: 2, ./*caret*/.foo };
        }
    """, """
        struct Foo(i32, i32);

        fn f() {
            let foo = Foo { 0: 0, 1: 1 };
            let bar = Foo { 0: 0/*caret*/, 1: 2, };
        }
    """)

    fun `test base struct in parentheses`() = doAvailableTest("""
        struct Foo { bar: i32, baz: i32 }

        fn f() {
            let foo = Foo { bar: 0, baz: 1 };
            let bar = Foo { bar: 2, ./*caret*/.(foo) };
        }
    """, """
        struct Foo { bar: i32, baz: i32 }

        fn f() {
            let foo = Foo { bar: 0, baz: 1 };
            let bar = Foo { bar: 2, baz: 0/*caret*/ };
        }
    """)
}
