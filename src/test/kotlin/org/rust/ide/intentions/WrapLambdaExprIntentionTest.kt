package org.rust.ide.intentions

import org.rust.lang.RustFileType
import org.rust.lang.RustTestCaseBase

class WrapLambdaExprIntentionTest : RustTestCaseBase() {
    override val dataPath = ""

    fun testAvailableWrapBraces() = doAvailableTest(
        """
        fn main() {
            |x| x <caret>* x
        }
        """
        ,
        """
        fn main() {
            |x| {
                x <caret>* x
            }
        }
        """
    )

    fun testUnavailableWrapBraces() = doUnavailableTest(
        """
        fn main() {
            |x| let<caret> a = 3;
        }
        """
    )

    private fun doAvailableTest(before: String, after: String) {
        myFixture.configureByText(RustFileType, before)
        myFixture.launchAction(WrapLambdaExprIntention())
        myFixture.checkResult(after)
    }

    private fun doUnavailableTest(before: String) {
        myFixture.configureByText(RustFileType, before)
        myFixture.launchAction(WrapLambdaExprIntention())
        myFixture.checkResult(before)
    }

}
