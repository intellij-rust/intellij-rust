package org.rust.ide.intentions

class UnElideLifetimesIntentionTest : RustIntentionTestBase(UnElideLifetimesIntention()) {
    fun testUnavailable1() = doUnavailableTest(
        """
        fn bar/*caret*/(x: i32) -> i32 {}
        """
    )

    fun testUnavailableBlockBody() = doUnavailableTest(
        """
        fn bar(x: &i32) {/*caret*/}
        """
    )

    fun testUnavailableNoArgs() = doUnavailableTest(
        """
        fn bar(/*caret*/) {}
        """
    )

    fun testUnavailableUnElided() = doUnavailableTest(
        """
        fn bar<'a>(x: &'a /*caret*/ i32) {}
        """
    )

    fun testSimple() = doAvailableTest(
        """
        fn foo(p: &/*caret*/ i32) -> & i32 { p }
        """
        ,
        """
        fn foo<'a>(p: &/*caret*/'a i32) -> &'a i32 { p }
        """
    )

    fun testGenericType() = doAvailableTest(
        """
        fn foo<T>(p1:/*caret*/ &i32, p2: T) -> & i32 { p }
        """
        ,
        """
        fn foo<'a, T>(p1:/*caret*/ &'a i32, p2: T) -> &'a i32 { p }
        """
    )

    fun testUnknown() = doAvailableTest(
        """
        fn foo(p1: &i32,/*caret*/ p2: &i32) -> &i32 { p2 }
        """
        ,
        """
        fn foo<'a, 'b>(p1: &'a i32,/*caret*/ p2: &'b i32) -> &'<selection>unknown</selection> i32 { p2 }
        """
    )



}
