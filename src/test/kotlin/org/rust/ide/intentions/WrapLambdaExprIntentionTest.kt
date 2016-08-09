package org.rust.ide.intentions

import org.rust.lang.RustTestCaseBase

class WrapLambdaExprIntentionTest : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/intentions/fixtures/wrap_lambda_expr/"

    fun testWrapBraces() = checkByFile {
        openFileInEditor("wrap_braces.rs")
        myFixture.launchAction(WrapLambdaExprIntention())
    }
}
