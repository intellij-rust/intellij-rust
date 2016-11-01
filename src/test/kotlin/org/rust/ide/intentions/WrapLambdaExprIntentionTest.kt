package org.rust.ide.intentions

class WrapLambdaExprIntentionTest : RustIntentionTestBase(WrapLambdaExprIntention()) {
    override val dataPath = ""

    fun testAvailableWrapBraces() = doAvailableTest(
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

    fun testUnavailableWrapBraces() = doUnavailableTest(
        """
        fn main() {
            |x| {/*caret*/ let a = 3; }
        }
        """
    )
}
