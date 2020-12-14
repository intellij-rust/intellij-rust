/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.visibility

import org.rust.ide.intentions.RsIntentionTestBase

class MakePrivateIntentionTest : RsIntentionTestBase(MakePrivateIntention::class) {
    fun `test available on function keyword`() = checkAvailableInSelectionOnly("""
        <selection>pub fn</selection> foo() {}
    """)

    fun `test available on function modifier`() = checkAvailableInSelectionOnly("""
        <selection>pub unsafe fn</selection> foo() {}
    """)

    fun `test unavailable on private item`() = doUnavailableTest("""
        /*caret*/fn foo() {}
    """)

    fun `test make restricted pub private`() = doAvailableTest("""
        pub(crate) /*caret*/fn foo() {}
    """, """
        /*caret*/fn foo() {}
    """)

    fun `test make function private`() = doAvailableTest("""
        pub /*caret*/fn foo() {}
    """, """
        /*caret*/fn foo() {}
    """)

    fun `test make struct private`() = doAvailableTest("""
        pub /*caret*/struct Foo {}
    """, """
        /*caret*/struct Foo {}
    """)

    fun `test make struct named field private`() = doAvailableTest("""
        struct Foo {
            pub /*caret*/a: u32
        }
    """, """
        struct Foo {
            /*caret*/a: u32
        }
    """)

    fun `test make struct tuple field private`() = doAvailableTest("""
        struct Foo(pub /*caret*/a: u32);
    """, """
        struct Foo(/*caret*/a: u32);
    """)

    fun `test make type alias private`() = doAvailableTest("""
        pub /*caret*/type Foo = u32;
    """, """
        /*caret*/type Foo = u32;
    """)

    fun `test make constant private`() = doAvailableTest("""
        pub /*caret*/const Foo: u32 = u32;
    """, """
        /*caret*/const Foo: u32 = u32;
    """)

    fun `test make use private`() = doAvailableTest("""
        pub use/*caret*/ foo::bar;
    """, """
        use/*caret*/ foo::bar;
    """)
}
