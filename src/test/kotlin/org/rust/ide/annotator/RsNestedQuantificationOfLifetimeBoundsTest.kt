/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsNestedQuantificationOfLifetimeBoundsTest : RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test E0316 one lifetime quantification`() = checkByText("""
        fn _foo<T>(t: T)
        where
            for<'a, 'b> &'a T: Tr<'a, 'b>,
        {}
    """)

    fun `test E0316 simple nested lifetime quantification`() = checkByText("""
        fn _foo<T>(t: T)
        where
            for<'a> &'a T: /*error descr="Nested quantification of lifetimes [E0316]"*/for<'b> Tr<'a, 'b>/*error**/,
        {}
    """)

    fun `test E0316 two nested lifetime quantifications`() = checkByText("""
        fn _foo<T>(t: T)
        where
            for<'a> &'a T:
                /*error descr="Nested quantification of lifetimes [E0316]"*/for<'b> Tr<'a, 'b>/*error**/
                + /*error descr="Nested quantification of lifetimes [E0316]"*/for<'b> Tr<'a, 'b>/*error**/,
        {}
    """)

    fun `test E0316 fix by removing inner for quantification`() = checkFixByText("Remove `for<'b> Tr<'a, 'b>` bound", """
        fn _foo<T>(t: T)
        where
            for<'a> &'a T: /*error descr="Nested quantification of lifetimes [E0316]"*/for<'b> Tr<'a, 'b>/*error**//*caret*/,
        {}
    """, """
        fn _foo<T>(t: T)
        where
            for<'a> &'a T:/*caret*/,
        {}
    """)
}
