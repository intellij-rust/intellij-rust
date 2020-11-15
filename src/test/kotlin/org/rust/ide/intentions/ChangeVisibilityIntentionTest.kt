/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class ChangeVisibilityIntentionTest : RsIntentionTestBase(ChangeVisibilityIntention::class) {
    fun `test unavailable on enum variant`() = doUnavailableTest("""
        enum Foo {
            F/*caret*/OO
        }
    """)

    fun `test unavailable on trait function`() = doUnavailableTest("""
        trait Foo {
            fn /*caret*/foo() {}
        }
    """)

    fun `test make restricted pub private`() = doAvailableTest("""
        pub(crate) fn foo()/*caret*/ {}
    """, """
        fn foo()/*caret*/ {}
    """)

    fun `test make function private`() = doAvailableTest("""
        pub fn /*caret*/foo() {}
    """, """
        fn /*caret*/foo() {}
    """)

    fun `test make function public`() = doAvailableTest("""
        fn /*caret*/foo() {}
    """, """
        pub(crate) fn /*caret*/foo() {}
    """)

    fun `test make async function public`() = doAvailableTest("""
        async fn /*caret*/foo() {}
    """, """
        pub(crate) async fn /*caret*/foo() {}
    """)

    fun `test make unsafe function public`() = doAvailableTest("""
        unsafe fn /*caret*/foo() {}
    """, """
        pub(crate) unsafe fn /*caret*/foo() {}
    """)

    fun `test make extern function public`() = doAvailableTest("""
        extern "C" fn /*caret*/foo() {}
    """, """
        pub(crate) extern "C" fn /*caret*/foo() {}
    """)

    fun `test make struct private`() = doAvailableTest("""
        pub struct /*caret*/Foo {}
    """, """
        struct /*caret*/Foo {}
    """)

    fun `test make struct public`() = doAvailableTest("""
        struct /*caret*/Foo {}
    """, """
        pub(crate) struct /*caret*/Foo {}
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

    fun `test make struct named field public`() = doAvailableTest("""
        struct Foo {
            /*caret*/a: u32
        }
    """, """
        struct Foo {
            /*caret*/pub(crate) a: u32
        }
    """)

    fun `test make struct tuple field private`() = doAvailableTest("""
        struct Foo(pub /*caret*/a: u32);
    """, """
        struct Foo(/*caret*/a: u32);
    """)

    fun `test make struct tuple field public`() = doAvailableTest("""
        struct Foo(/*caret*/a: u32);
    """, """
        struct Foo(/*caret*/pub(crate) a: u32);
    """)

    fun `test make type alias private`() = doAvailableTest("""
        pub type Foo /*caret*/ = u32;
    """, """
        type Foo /*caret*/ = u32;
    """)

    fun `test make type alias public`() = doAvailableTest("""
        type Foo /*caret*/ = u32;
    """, """
        pub(crate) type Foo /*caret*/ = u32;
    """)

    fun `test make constant private`() = doAvailableTest("""
        pub const Foo: u32 /*caret*/ = u32;
    """, """
        const Foo: u32 /*caret*/ = u32;
    """)

    fun `test make constant public`() = doAvailableTest("""
        const Foo: u32 /*caret*/ = u32;
    """, """
        pub(crate) const Foo: u32 /*caret*/ = u32;
    """)

    fun `test make use private`() = doAvailableTest("""
        pub use/*caret*/ foo::bar;
    """, """
        use/*caret*/ foo::bar;
    """)

    fun `test make use public`() = doAvailableTest("""
        use/*caret*/ foo::bar;
    """, """
        pub(crate) use/*caret*/ foo::bar;
    """)
}
