/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsMultipleRelaxedBoundsTest: RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test E0203 sample`() = checkByText("""
        struct Bad<<error descr="Type parameter has more than one relaxed default bound, only one is supported [E0203]">T: ?Sized + ?Send</error>>{
            inner: T
        }
    """)

    fun `test E0203 compiler test -- no-patterns-in-args-macro`() = checkByText("""
        struct S5<<error descr="Type parameter has more than one relaxed default bound, only one is supported [E0203]">T</error>>(*const T) where T: ?Trait<'static> + ?Sized;
    """)

    fun `test E0203 where clause`() = checkByText("""
        struct Bad<<error descr="Type parameter has more than one relaxed default bound, only one is supported [E0203]">T: ?Sized</error>> where T: ?Sized {
            inner: T
        }
    """)
}
