/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class AddImplIntentionTest : RsIntentionTestBase(AddImplIntention::class) {
    fun `test simple struct`() = doAvailableTest("""
        struct Hey/*caret*/ {
            var: i32
        }
    """, """
        struct Hey {
            var: i32
        }

        impl Hey {/*caret*/}
    """)

    fun `test simple enum`() = doAvailableTest("""
        enum E/*caret*/ { }
    """, """
        enum E { }

        impl E {/*caret*/}
    """)

    fun `test generic struct`() = doAvailableTest("""
        struct S<'a, 'b: 'a, U: Clone, V, const N: usize>/*caret*/ where V: Copy { }
    """, """
        struct S<'a, 'b: 'a, U: Clone, V, const N: usize> where V: Copy { }

        impl<'a, 'b: 'a, U: Clone, V, const N: usize> S<'a, 'b, U, V, N> where V: Copy {/*caret*/}
    """)
}
