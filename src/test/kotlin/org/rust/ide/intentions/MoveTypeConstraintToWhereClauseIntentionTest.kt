/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class MoveTypeConstraintToWhereClauseIntentionTest : RsIntentionTestBase(MoveTypeConstraintToWhereClauseIntention()) {
    fun `test function with return`() = doAvailableTest(
        """ fn foo<T: Send,/*caret*/ F: Sync>(t: T, f: F) -> i32 { 0 } """,
        """ fn foo<T, F>(t: T, f: F) -> i32 where T: Send, F: Sync/*caret*/ { 0 } """
    )

    fun `test lifetimes and traits`() = doAvailableTest(
        """ fn foo<'a, 'b: 'a, T: Send,/*caret*/ F: Sync>(t: &'a T, f: &'b F) { } """,
        """ fn foo<'a, 'b, T, F>(t: &'a T, f: &'b F) where 'b: 'a, T: Send, F: Sync/*caret*/ { } """
    )

    fun `test multiple bounds`() = doAvailableTest(
        """ fn foo<T: /*caret*/Send + Sync>(t: T, f: F) { } """,
        """ fn foo<T>(t: T, f: F) where T: Send + Sync/*caret*/ { } """
    )

    fun `test multiple lifetimes`() = doAvailableTest(
        """ fn foo<'a, /*caret*/'b: 'a>(t: &'a i32, f: &'b i32) { } """,
        """ fn foo<'a, 'b>(t: &'a i32, f: &'b i32) where 'b: 'a/*caret*/ { } """
    )

    fun `test multiple traits`() = doAvailableTest(
        """ fn foo<T: Send,/*caret*/ F: Sync>(t: T, f: F) { } """,
        """ fn foo<T, F>(t: T, f: F) where T: Send, F: Sync/*caret*/ { } """
    )

    fun `test type item element`() = doAvailableTest(
        """ type O<T: /*caret*/Copy> = Option<T>; """,
        """ type O<T> where T: Copy/*caret*/ = Option<T>; """
    )

    fun `test impl item element`() = doAvailableTest(
        """ impl<T: /*caret*/Copy> Foo<T> {} """,
        """ impl<T> Foo<T> where T: Copy/*caret*/ {} """
    )

    fun `test trait item element`() = doAvailableTest(
        """ trait Foo<T:/*caret*/ Copy> {} """,
        """ trait Foo<T> where T: Copy/*caret*/ {} """
    )

    fun `test struct item element`() = doAvailableTest(
        """ struct Foo<T:/*caret*/ Copy> { x: T } """,
        """ struct Foo<T> where T: Copy/*caret*/ { x: T } """
    )

    fun `test tuple struct item element`() = doAvailableTest(
        """ struct Foo<T:/*caret*/ Copy>(T); """,
        """ struct Foo<T>(T) where T: Copy/*caret*/; """
    )

    fun `test enum item element`() = doAvailableTest(
        """ enum Foo<T:/*caret*/ Copy> { X(T) } """,
        """ enum Foo<T> where T: Copy/*caret*/ { X(T) } """
    )

    fun `test partial where clause exists`() = doAvailableTest("""
        impl<Fut, Req, Func:/*caret*/ Fn(Req) -> Fut, Resp, Err> Service for ServiceFn<Func>
        where
            Fut: IntoFuture<Item=Resp, Error=Err>,
        {
    """, """
        impl<Fut, Req, Func, Resp, Err> Service for ServiceFn<Func>
        where
            Fut: IntoFuture<Item=Resp, Error=Err>, Func: Fn(Req) -> Fut
        {
    """)

    fun `test partial where clause exists adds comma`() = doAvailableTest("""
        struct Spam<Foo, Bar: /*caret*/Future> where Foo: Iterator { }
    """, """
        struct Spam<Foo, Bar> where Foo: Iterator, Bar: Future { }
    """)

    fun `test no lifetime bounds`() = doUnavailableTest(""" fn foo<'a, /*caret*/'b>(t: &'a i32, f: &'b i32) { } """)

    fun `test no trait bounds`() = doUnavailableTest(""" fn foo<T, /*caret*/F>(t: T, f: F) { } """)
}
