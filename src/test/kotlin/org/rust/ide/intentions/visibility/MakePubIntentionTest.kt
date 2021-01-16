/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.visibility

import org.rust.ide.intentions.RsIntentionTestBase

class MakePubIntentionTest : RsIntentionTestBase(MakePubIntention::class) {
    fun `test available on function keyword`() = checkAvailableInSelectionOnly("""
        <selection>fn</selection> foo() {}
    """)

    fun `test available on function modifier`() = checkAvailableInSelectionOnly("""
        <selection>unsafe fn</selection> foo() {}
    """)

    fun `test unavailable on enum variant`() = doUnavailableTest("""
        enum Foo {
            F/*caret*/OO
        }
    """)

    fun `test unavailable on trait function`() = doUnavailableTest("""
        trait Foo {
            f/*caret*/n foo() {}
        }
    """)

    fun `test unavailable on trait impl function`() = doUnavailableTest("""
        trait Foo {
            fn foo() {}
        }
        impl Foo for () {
            f/*caret*/n foo() {}
        }
    """)

    fun `test unavailable on impl block`() = doUnavailableTest("""
        struct Foo;
        impl Foo {
            fn foo() {}
        }/*caret*/
    """)

    fun `test available on impl function`() = doAvailableTest("""
        struct Foo;
        impl Foo {
            fn/*caret*/ foo() {}
        }
    """, """
        struct Foo;
        impl Foo {
            pub fn/*caret*/ foo() {}
        }
    """)

    fun `test unavailable on pub item`() = doUnavailableTest("""
        pub /*caret*/fn foo() {}
    """)

    fun `test make function public`() = doAvailableTest("""
        /*caret*/fn foo() {}
    """, """
        /*caret*/pub fn foo() {}
    """)

    fun `test make async function public`() = doAvailableTest("""
        async /*caret*/fn foo() {}
    """, """
        pub async /*caret*/fn foo() {}
    """)

    fun `test make unsafe function public`() = doAvailableTest("""
        unsafe /*caret*/fn foo() {}
    """, """
        pub unsafe /*caret*/fn foo() {}
    """)

    fun `test make extern function public`() = doAvailableTest("""
        extern "C" /*caret*/fn foo() {}
    """, """
        pub extern "C" /*caret*/fn foo() {}
    """)

    fun `test make struct public`() = doAvailableTest("""
        /*caret*/struct Foo {}
    """, """
        /*caret*/pub struct Foo {}
    """)

    fun `test make struct named field public`() = doAvailableTest("""
        struct Foo {
            /*caret*/a: u32
        }
    """, """
        struct Foo {
            /*caret*/pub a: u32
        }
    """)

    fun `test make struct tuple field public`() = doAvailableTest("""
        struct Foo(/*caret*/a: u32);
    """, """
        struct Foo(/*caret*/pub a: u32);
    """)

    fun `test make type alias public`() = doAvailableTest("""
        /*caret*/type Foo = u32;
    """, """
        /*caret*/pub type Foo = u32;
    """)

    fun `test make constant public`() = doAvailableTest("""
        /*caret*/const FOO: u32 = u32;
    """, """
        /*caret*/pub const FOO: u32 = u32;
    """)

    fun `test make use public`() = doAvailableTest("""
        use/*caret*/ foo::bar;
    """, """
        pub use/*caret*/ foo::bar;
    """)
}
