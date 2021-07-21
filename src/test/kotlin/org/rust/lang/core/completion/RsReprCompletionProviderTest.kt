/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.rust.lang.core.types.ty.TyInteger

class RsReprCompletionProviderTest : RsCompletionTestBase() {

    fun `test complete on enum`() = checkContainsCompletion(listOf("C", "transparent", "align()") + TyInteger.NAMES, """
        #[repr(/*caret*/)]
        enum E { A }
    """)

    fun `test complete on struct`() = checkContainsCompletion(listOf("C", "transparent", "align()", "packed", "packed()", "simd"), """
        #[repr(/*caret*/)]
        struct S { f: i32 }
    """)

    fun `test complete 'transparent' on enum`() = doSingleCompletion("""
        #[repr(trans/*caret*/)]
        enum E { A }
    """, """
        #[repr(transparent/*caret*/)]
        enum E { A }
    """)

    fun `test complete attr under cfg_attr`() = doSingleCompletion("""
        #[cfg_attr(unix, repr(trans/*caret*/))]
        enum E { A }
    """, """
        #[cfg_attr(unix, repr(transparent/*caret*/))]
        enum E { A }
    """)

    fun `test no packed layout inside another attribute`() = checkNotContainsCompletion("packed", """
        #[foo(trans/*caret*/)]
        enum E {}
    """)

    // The packed layout is applicable only to struct and union.
    fun `test no packed layout on enum`() = checkNotContainsCompletion("packed", """
        #[repr(pack/*caret*/)]
        enum E {}
    """)

    // Primitive representations are only applicable to enumerations.
    fun `test no primitive repr on struct`() = checkNotContainsCompletion("isize", """
        #[repr("isi/*caret*/")]
        struct S {}
    """)

}
