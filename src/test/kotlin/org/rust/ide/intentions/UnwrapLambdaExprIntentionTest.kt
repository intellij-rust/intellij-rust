package org.rust.ide.intentions

import org.rust.lang.RustTestCaseBase

class UnwrapLambdaExprIntentionTest : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/intentions/fixtures/unwrap_lambda_expr/"

    fun testAvailableUnwrapBraces() = checkByFile {
        openFileInEditor("available_unwrap_braces.rs")
        myFixture.launchAction(UnwrapLambdaExprIntention())
    }

    fun testUnavailableUnwrapBraces() = checkByFile {
        openFileInEditor("unavailable_unwrap_braces.rs")
        myFixture.launchAction(UnwrapLambdaExprIntention())
    }
}
