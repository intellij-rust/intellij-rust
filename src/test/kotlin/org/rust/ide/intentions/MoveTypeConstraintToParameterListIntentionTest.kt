/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class MoveTypeConstraintToParameterListIntentionTest : RsIntentionTestBase(MoveTypeConstraintToParameterListIntention()) {

    fun `test lifetimes and traits`() = doAvailableTest(
        """ fn foo<'a, 'b, 'c, T, U>() /*caret*/where 'b: 'a, 'c:'a, T: Clone, U: Copy {} """,
        """ fn foo<'a, 'b: 'a, 'c: 'a, T: Clone, U: Copy>/*caret*/() {} """
    )

    fun `test multiple bounds`() = doAvailableTest(
        """ fn foo<'a, 'b, 'c, 'd, T, U>() where /*caret*/'c: 'a + 'b, 'd: 'a + 'b, T: Clone + Copy, U: Copy + Debug {} """,
        """ fn foo<'a, 'b, 'c: 'a + 'b, 'd: 'a + 'b, T: Clone + Copy, U: Copy + Debug>/*caret*/() {} """
    )

    fun `test divided bounds`() = doAvailableTest(
        """ fn foo<'a, 'b, 'c, 'd, T>() where 'd: 'a + 'b, /*caret*/'d: 'c, T: Clone + Copy, T: Debug {} """,
        """ fn foo<'a, 'b, 'c, 'd: 'a + 'b + 'c, T: Clone + Copy + Debug>/*caret*/() {} """
    )

    fun `test duplicate bounds`() = doAvailableTest(
        """ fn foo<'a, 'b, 'c: 'a, T: Clone>() where 'c: 'a + 'b, T: Clone + Copy/*caret*/ {} """,
        """ fn foo<'a, 'b, 'c: 'a + 'b, T: Clone + Copy>/*caret*/() {} """
    )

    fun `test type item element`() = doAvailableTest(
        """ type O<T> /*caret*/where T: Copy = Option<T>; """,
        """ type O<T: Copy>/*caret*/ = Option<T>; """
    )

    fun `test impl item element`() = doAvailableTest(
        """ impl<T> Foo<T> /*caret*/where T: Copy {} """,
        """ impl<T: Copy>/*caret*/ Foo<T> {} """
    )

    fun `test trait item element`() = doAvailableTest(
        """ trait Foo<T> /*caret*/where T: Copy {} """,
        """ trait Foo<T: Copy>/*caret*/ {} """
    )

    fun `test struct item element`() = doAvailableTest(
        """ struct Foo<T> /*caret*/where T: Copy { x: T } """,
        """ struct Foo<T: Copy>/*caret*/ { x: T } """
    )

    fun `test tuple struct item element`() = doAvailableTest(
        """ struct Foo<T>(T) /*caret*/where T: Copy; """,
        """ struct Foo<T: Copy>/*caret*/(T); """
    )

    fun `test enum item element`() = doAvailableTest(
        """ enum Foo<T> /*caret*/where T: Copy { X(T) } """,
        """ enum Foo<T: Copy>/*caret*/ { X(T) } """
    )

    fun `test no type`() = doUnavailableTest(""" fn foo<'a>() /*caret*/where T: Clone {}""")

    fun `test no lifetime`() = doUnavailableTest(""" fn foo<T>() /*caret*/where 'b: 'a {}""")
}
