/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.IntentionAction
import org.rust.RsTestBase

abstract class RsIntentionTestPlatformBase : RsTestBase() {
    protected fun checkPreviewAndLaunchAction(intention: IntentionAction, preview: String?) {
        myFixture.launchAction(intention)
    }
}
