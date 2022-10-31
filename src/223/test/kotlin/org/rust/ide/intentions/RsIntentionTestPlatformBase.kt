/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.IntentionAction
import org.rust.RsTestBase

// BACKCOMPAT: 2022.2. Merge into `RsIntentionTestBase`
abstract class RsIntentionTestPlatformBase : RsTestBase() {
    protected fun checkPreviewAndLaunchAction(intention: IntentionAction, preview: String?) {
        val text = myFixture.getIntentionPreviewText(intention)
        check(text != null) { "No preview for intention ${intention.text}" }
        myFixture.launchAction(intention)
        assertEquals(
            "Intention ${intention.text} produced different result when invoked in preview mode",
            preview ?: myFixture.file.text,
            text
        )
    }
}
