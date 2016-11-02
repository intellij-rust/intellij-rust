package org.rust.ide.intentions

class MoveTypeConstraintToWhereClauseIntentionTest : RustIntentionTestBase(MoveTypeConstraintToWhereClauseIntention()) {
    fun testFunctionWithReturn() = doAvailableTest(
        """ fn foo<T: Send,/*caret*/ F: Sync>(t: T, f: F) -> i32 { 0 } """,
        """ fn foo<T, F>(t: T, f: F) -> i32 where T: Send, F: Sync/*caret*/ { 0 } """
    )

    fun testLifetimesAndTraits() = doAvailableTest(
        """ fn foo<'a, 'b: 'a, T: Send,/*caret*/ F: Sync>(t: &'a T, f: &'b F) { } """,
        """ fn foo<'a, 'b, T, F>(t: &'a T, f: &'b F) where 'b: 'a, T: Send, F: Sync/*caret*/ { } """
    )

    fun testMultipleBounds() = doAvailableTest(
        """ fn foo<T: /*caret*/Send + Sync>(t: T, f: F) { } """,
        """ fn foo<T>(t: T, f: F) where T: Send + Sync/*caret*/ { } """
    )

    fun testMultipleLifetimes() = doAvailableTest(
        """ fn foo<'a, /*caret*/'b: 'a>(t: &'a i32, f: &'b i32) { } """,
        """ fn foo<'a, 'b>(t: &'a i32, f: &'b i32) where 'b: 'a/*caret*/ { } """
    )

    fun testMultipleTraits() = doAvailableTest(
        """ fn foo<T: Send,/*caret*/ F: Sync>(t: T, f: F) { } """,
        """ fn foo<T, F>(t: T, f: F) where T: Send, F: Sync/*caret*/ { } """
    )

    fun testNoLifetimeBounds() = doUnavailableTest(""" fn foo<'a, /*caret*/'b>(t: &'a i32, f: &'b i32) { } """)

    fun testNoTraitBounds() = doUnavailableTest(""" fn foo<T, /*caret*/F>(t: T, f: F) { } """)
}
