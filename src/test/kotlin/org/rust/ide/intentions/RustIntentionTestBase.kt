package org.rust.ide.intentions

import com.intellij.codeInsight.intention.IntentionAction
import org.intellij.lang.annotations.Language
import org.rust.lang.RustTestCaseBase

abstract class RustIntentionTestBase(val intention: IntentionAction): RustTestCaseBase() {
    protected fun doAvailableTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        InlineFile(before).withCaret()
        myFixture.launchAction(intention)
        myFixture.checkResult(after.replace("/*caret*/", "<caret>"))
    }

    protected fun doUnavailableTest(@Language("Rust") before: String) {
        InlineFile(before).withCaret()
        myFixture.launchAction(intention)
        myFixture.checkResult(before.replace("/*caret*/", "<caret>"))
    }
}
