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

    protected fun doUnavailableTest(@Language("Rust") before: String) {
        InlineFile(before).withCaret()
        check(intention.familyName !in myFixture.availableIntentions.mapNotNull { it.familyName }) {
            "\"$intention\" intention should not be available"
        }
    }
}
