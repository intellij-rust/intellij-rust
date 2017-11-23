/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class UnElideLifetimesIntentionTest : RsIntentionTestBase(UnElideLifetimesIntention()) {
    fun `test unavailable`() = doUnavailableTest(
        """
        fn bar/*caret*/(x: i32) -> i32 {}
        """
    )

    fun `test unavailable block body`() = doUnavailableTest(
        """
        fn bar(x: &i32) {/*caret*/}
        """
    )

    fun `test unavailable no args`() = doUnavailableTest(
        """
        fn bar(/*caret*/) {}
        """
    )

    fun `test unavailable un elided`() = doUnavailableTest(
        """
        fn bar<'a>(x: &'a /*caret*/ i32) {}
        """
    )

    fun `test simple`() = doAvailableTest(
        """
        fn foo(p: &/*caret*/ i32) -> & i32 { p }
        """
        ,
        """
        fn foo<'a>(p: &/*caret*/'a i32) -> &'a i32 { p }
        """
    )

    fun `test generic type`() = doAvailableTest(
        """
        fn foo<T>(p1:/*caret*/ &i32, p2: T) -> & i32 { p }
        """
        ,
        """
        fn foo<'a, T>(p1:/*caret*/ &'a i32, p2: T) -> &'a i32 { p }
        """
    )

    fun `test unknown`() = doAvailableTest(
        """
        fn foo(p1: &i32,/*caret*/ p2: &i32) -> &i32 { p2 }
        """
        ,
        """
        fn foo<'a, 'b>(p1: &'a i32,/*caret*/ p2: &'b i32) -> &'<selection>unknown</selection> i32 { p2 }
        """
    )

    fun `test method decl`() = doAvailableTest(
        """
        trait Foo {
            fn /*caret*/bar(&self, x: &i32, y: &i32, x: i32) -> &i32;
        }
        """
        ,
        """
        trait Foo {
            fn /*caret*/bar<'a, 'b, 'c>(&'a self, x: &'b i32, y: &'c i32, x: i32) -> &'a i32;
        }
        """
    )

    fun `test method impl`() = doAvailableTest(
        """
        trait Foo {
            fn bar(&self, x: &i32, y: &i32, x: i32) -> &i32;
        }
        struct S {}
        impl Foo for S {
            fn /*caret*/bar(&self, x: &i32, y: &i32, x: i32) -> &i32 {
                unimplemented!()
            }
        }
        """
        ,
        """
        trait Foo {
            fn bar(&self, x: &i32, y: &i32, x: i32) -> &i32;
        }
        struct S {}
        impl Foo for S {
            fn /*caret*/bar<'a, 'b, 'c>(&'a self, x: &'b i32, y: &'c i32, x: i32) -> &'a i32 {
                unimplemented!()
            }
        }
        """
    )
}
