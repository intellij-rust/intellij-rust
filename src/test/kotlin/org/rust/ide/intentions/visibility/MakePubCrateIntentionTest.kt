/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.visibility

import org.rust.ide.intentions.RsIntentionTestBase

class MakePubCrateIntentionTest : RsIntentionTestBase(MakePubCrateIntention::class) {
    fun `test available on function keyword`() = checkAvailableInSelectionOnly("""
        <selection>fn</selection> foo() {}
    """)

    fun `test available on function modifier`() = checkAvailableInSelectionOnly("""
        <selection>unsafe fn</selection> foo() {}
    """)

    fun `test unavailable on pub(crate) item`() = doUnavailableTest("""
        pub(crate) /*caret*/fn foo() {}
    """)

    fun `test make restricted visibility pub(crate)`() = doAvailableTest("""
        mod bar {
            pub(in crate::bar) /*caret*/fn foo() {}
        }
    """, """
        mod bar {
            pub(crate) /*caret*/fn foo() {}
        }
    """)

    fun `test make function pub(crate)`() = doAvailableTest("""
        /*caret*/fn foo() {}
    """, """
        /*caret*/pub(crate) fn foo() {}
    """)

    fun `test make async function pub(crate)`() = doAvailableTest("""
        async /*caret*/fn foo() {}
    """, """
        pub(crate) async /*caret*/fn foo() {}
    """)

    fun `test make unsafe function pub(crate)`() = doAvailableTest("""
        unsafe /*caret*/fn foo() {}
    """, """
        pub(crate) unsafe /*caret*/fn foo() {}
    """)

    fun `test make extern function pub(crate)`() = doAvailableTest("""
        extern "C" /*caret*/fn foo() {}
    """, """
        pub(crate) extern "C" /*caret*/fn foo() {}
    """)

    fun `test make struct pub(crate)`() = doAvailableTest("""
        /*caret*/struct Foo {}
    """, """
        /*caret*/pub(crate) struct Foo {}
    """)

    fun `test make struct named field pub(crate)`() = doAvailableTest("""
        struct Foo {
            /*caret*/a: u32
        }
    """, """
        struct Foo {
            /*caret*/pub(crate) a: u32
        }
    """)

    fun `test make struct tuple field pub(crate)`() = doAvailableTest("""
        struct Foo(/*caret*/a: u32);
    """, """
        struct Foo(/*caret*/pub(crate) a: u32);
    """)

    fun `test make type alias pub(crate)`() = doAvailableTest("""
        /*caret*/type Foo = u32;
    """, """
        /*caret*/pub(crate) type Foo = u32;
    """)

    fun `test make constant pub(crate)`() = doAvailableTest("""
        /*caret*/const Foo: u32 = u32;
    """, """
        /*caret*/pub(crate) const Foo: u32 = u32;
    """)

    fun `test make use pub(crate)`() = doAvailableTest("""
        use/*caret*/ foo::bar;
    """, """
        pub(crate) use/*caret*/ foo::bar;
    """)
}
