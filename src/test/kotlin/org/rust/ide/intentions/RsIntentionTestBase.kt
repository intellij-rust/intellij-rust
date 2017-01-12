package org.rust.ide.intentions

import com.intellij.codeInsight.intention.IntentionAction
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase

abstract class RsIntentionTestBase(val intention: IntentionAction) : RsTestBase() {
    final override val dataPath: String get() = ""

    protected fun doAvailableTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        InlineFile(before).withCaret()
        myFixture.launchAction(intention)
        myFixture.checkResult(replaceCaretMarker(after))
    }

    protected fun doUnavailableTest(@Language("Rust") before: String) = doAvailableTest(before, before)
}
