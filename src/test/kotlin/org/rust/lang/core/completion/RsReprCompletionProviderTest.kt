/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsReprCompletionProviderTest : RsCompletionTestBase() {

    fun `test complete on enum`() = doSingleCompletion("""
        #[repr(trans/*caret*/)]
        enum E {}
    """, """
        #[repr(transparent/*caret*/)]
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
