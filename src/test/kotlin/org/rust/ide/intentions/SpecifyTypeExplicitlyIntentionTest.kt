/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class SpecifyTypeExplicitlyIntentionTest : RsIntentionTestBase(SpecifyTypeExplicitlyIntention()) {
    fun `test inferred type`() = doAvailableTest(
        """ fn main() { let var/*caret*/ = 42; } """,
        """ fn main() { let var: i32 = 42; } """
    )

    fun `test generic type`() = doAvailableTest(
        """struct A<T>(T);fn main() { let var/*caret*/ = A(42); } """,
        """struct A<T>(T);fn main() { let var: A<i32> = A(42); } """
    )

    fun `test not inferred type`() = doUnavailableTest(
        """ fn main() { let var/*caret*/ = a; } """
    )
}
