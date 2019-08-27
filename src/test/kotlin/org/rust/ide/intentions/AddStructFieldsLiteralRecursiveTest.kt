/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class AddStructFieldsLiteralRecursiveTest : RsIntentionTestBase(AddStructFieldsLiteralRecursiveIntention()) {
    fun `test struct literal with inner struct`() = doAvailableTest(
        """
            struct Foo { bar: i32, baz: i32, quux: Bar }
            
            struct Bar { spam: i32 }
            
            fn f() {
                let foo = Foo { bar: 0, baz: 1, quux: Bar { spam: 2} };
                let bar = Foo { bar: 2, ./*caret*/.foo };
            }
        """,
        """
            struct Foo { bar: i32, baz: i32, quux: Bar }
            
            struct Bar { spam: i32 }
            
            fn f() {
                let foo = Foo { bar: 0, baz: 1, quux: Bar { spam: 2} };
                let bar = Foo { bar: 2, baz: 0/*caret*/, quux: Bar { spam: 0 } };
            }
        """
    )

    fun `test struct literal with inner tuple struct`() = doAvailableTest(
        """
            struct Foo { bar: i32, baz: i32, quux: Bar }
            
            struct Bar(i32);
            
            fn f() {
                let foo = Foo { bar: 0, baz: 1, quux: Bar(2) };
                let bar = Foo { bar: 2, ./*caret*/.foo };
            }
        """,
        """
            struct Foo { bar: i32, baz: i32, quux: Bar }
            
            struct Bar(i32);
            
            fn f() {
                let foo = Foo { bar: 0, baz: 1, quux: Bar(2) };
                let bar = Foo { bar: 2, baz: 0/*caret*/, quux: Bar(0) };
            }
        """
    )

    fun `test struct literal with double inner tuple structs`() = doAvailableTest(
        """
            struct Foo { bar: i32, baz: i32, quux: Bar }
            
            struct Bar(Baz, i32);
            
            struct Baz(i32, f64);
            
            fn f() {
                let foo = Foo { bar: 0, baz: 1, quux: Bar(Baz(1, 3.14), 2) };
                let bar = Foo { bar: 2, ./*caret*/.foo };
            }
        """,
        """
            struct Foo { bar: i32, baz: i32, quux: Bar }
            
            struct Bar(Baz, i32);
            
            struct Baz(i32, f64);
            
            fn f() {
                let foo = Foo { bar: 0, baz: 1, quux: Bar(Baz(1, 3.14), 2) };
                let bar = Foo { bar: 2, baz: 0/*caret*/, quux: Bar(Baz(0, 0.0), 0) };
            }
        """
    )
}
