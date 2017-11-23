/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.intellij.lang.annotations.Language
import org.rust.lang.RsFileType
import org.rust.lang.RsTestBase

class UnwrapSingleExprIntentionTest : RsTestBase() {
    fun `test available lambda unwrap braces single expression`() = doAvailableTest(
        """
        fn main() {
            {
                4<caret>2
            }
        }
        """
        ,
        """
        fn main() {
            4<caret>2
        }
        """
    )

    fun `test available lambda unwrap braces`() = doAvailableTest(
        """
        fn main() {
            |x| { x *<caret> x }
        }
        """
        ,
        """
        fn main() {
            |x| x *<caret> x
        }
        """
    )

    fun `test available unwrap braces single expression if`() = doAvailableTest(
        """
        fn main() {
            let a = {
                if (true) {
                    42
                } else<caret> {
                    43
                }
            };
        }
        """
        ,
        """
        fn main() {
            let a = if (true) {
                42
            } else<caret> {
                43
            };
        }
        """
    )

    fun `test available lambda unwrap braces single statement`() = doUnavailableTest(
        """
        fn main() {
            {
                <caret>42;
            }
        }
        """
    )

    fun `test unavailable unwrap braces`() = doUnavailableTest(
        """
        fn main() {
            |x| { let a = 3; x *<caret> a
            }
        """
    )

    fun `test unavailable unwrap braces let`() = doUnavailableTest(
        """
        fn main() {
            {
                <caret>let a = 5;
            }
        }
        """
    )

    fun `test unavailable unwrap braces unsafe`() = doUnavailableTest(
        """
        fn main() {
            let wellThen = unsafe<caret> { magic() };
        }
        """
    )

    private fun doAvailableTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        myFixture.configureByText(RsFileType, before)
        myFixture.launchAction(UnwrapSingleExprIntention())
        myFixture.checkResult(after)
    }

    private fun doUnavailableTest(@Language("Rust") before: String) {
        myFixture.configureByText(RsFileType, before)
        myFixture.launchAction(UnwrapSingleExprIntention())
        myFixture.checkResult(before)
    }
}
