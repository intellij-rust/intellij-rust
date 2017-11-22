/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class WrapLambdaExprIntentionTest : RsIntentionTestBase(WrapLambdaExprIntention()) {
    fun `test available wrap braces`() = doAvailableTest(
        """
        fn main() {
            |x| x /*caret*/* x
        }
        """
        ,
        """
        fn main() {
            |x| {
                x /*caret*/* x
            }
        }
        """
    )

    fun `test unavailable wrap braces`() = doUnavailableTest(
        """
        fn main() {
            |x| {/*caret*/ let a = 3; }
        }
        """
    )
}
