/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsTooManyLifetimeBoundsOnTraitObjectTest : RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test E0226 one lifetime bound`() = checkByText("""
        trait Foo {}
        type T1<'a> = dyn Foo + 'a;
    """)

    fun `test E0226 typedef with two lifetime bounds`() = checkByText("""
        trait Foo {}
        type T<'a, 'b> = /*error descr="Only a single explicit lifetime bound is permitted [E0226]"*/dyn Foo + 'a + 'b/*error**/;
    """)

    fun `test E0226 typedef with two lifetime bounds in mixed order`() = checkByText("""
        trait Foo {}
        type T<'a, 'b> = /*error descr="Only a single explicit lifetime bound is permitted [E0226]"*/dyn 'a + Foo + 'b/*error**/;
    """)

    fun `test E0226 trait object as function argument with two lifetime bounds`() = checkByText("""
        trait Foo {}
        fn _bar<'b, 'c>(_: Box</*error descr="Only a single explicit lifetime bound is permitted [E0226]"*/dyn Foo + 'b + 'c/*error**/>) {}
    """)

    fun `test E0226 fix by removing first lt`() = checkFixByText("Remove `'a` bound", """
        trait Foo {}
        type T<'a, 'b> = /*error descr="Only a single explicit lifetime bound is permitted [E0226]"*/dyn 'a + Foo + 'b/*error**//*caret*/;
    """, """
        trait Foo {}
        type T<'a, 'b> = dyn Foo + 'b/*caret*/;
    """)

    fun `test E0226 fix by removing middle lt`() = checkFixByText("Remove `'a` bound", """
        trait Foo {}
        type T<'a, 'b> = /*error descr="Only a single explicit lifetime bound is permitted [E0226]"*/dyn Foo + 'a + 'b/*error**//*caret*/;
    """, """
        trait Foo {}
        type T<'a, 'b> = dyn Foo + 'b/*caret*/;
    """)

    fun `test E0226 fix by removing last lt`() = checkFixByText("Remove `'b` bound", """
        trait Foo {}
        type T<'a, 'b> = /*error descr="Only a single explicit lifetime bound is permitted [E0226]"*/dyn 'a + Foo + 'b/*error**//*caret*/;
    """, """
        trait Foo {}
        type T<'a, 'b> = dyn 'a + Foo/*caret*/;
    """)
}
