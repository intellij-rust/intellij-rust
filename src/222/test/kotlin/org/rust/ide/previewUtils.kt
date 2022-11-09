/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

fun CodeInsightTestFixture.checkPreviewAndLaunchAction(intention: IntentionAction, preview: String?) {
    launchAction(intention)
}

fun CodeInsightTestFixture.checkNoPreview(intention: IntentionAction) {}
