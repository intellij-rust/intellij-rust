package org.rust.ide.intentions

import org.rust.lang.RsFileType
import org.rust.lang.RsTestBase

class UnwrapSingleExprIntentionTest : RsTestBase() {
    override val dataPath = ""

    fun testAvailableLambdaUnwrapBracesSingleExpression() = doAvailableTest(
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

    fun testAvailableLambdaUnwrapBraces() = doAvailableTest(
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

    fun testAvailableUnwrapBracesSingleExpressionIf() = doAvailableTest(
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

    fun testAvailableLambdaUnwrapBracesSingleStatement() = doUnavailableTest(
        """
        fn main() {
            {
                <caret>42;
            }
        }
        """
    )

    fun testUnavailableUnwrapBraces() = doUnavailableTest(
        """
        fn main() {
            |x| { let a = 3; x *<caret> a
            }
        """
    )

    fun testUnavailableUnwrapBracesLet() = doUnavailableTest(
        """
        fn main() {
            {
                <caret>let a = 5;
            }
        }
        """
    )

    private fun doAvailableTest(before: String, after: String) {
        myFixture.configureByText(RsFileType, before)
        myFixture.launchAction(UnwrapSingleExprIntention())
        myFixture.checkResult(after)
    }

    private fun doUnavailableTest(before: String) {
        myFixture.configureByText(RsFileType, before)
        myFixture.launchAction(UnwrapSingleExprIntention())
        myFixture.checkResult(before)
    }
}
