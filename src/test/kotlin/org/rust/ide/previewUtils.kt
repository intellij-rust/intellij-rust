/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFixAsIntentionAdapter
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.Assert.assertEquals

fun CodeInsightTestFixture.checkPreviewAndLaunchAction(intention: IntentionAction, preview: String?) {
    val actualPreview = getIntentionPreviewText(intention)
    check(actualPreview != null) {
        "No intention preview for `${intention.getClassName()}`. " +
            "Either support preview or pass `previewExpected = false`"
    }
    launchAction(intention)
    val expectedPreview = preview?.trimIndent() ?: file.text
    assertEquals(
        "Intention `${intention.getClassName()}` produced different result when invoked in preview mode",
        expectedPreview,
        actualPreview
    )
}

fun CodeInsightTestFixture.checkNoPreview(intention: IntentionAction) {
    val previewInfo = IntentionPreviewPopupUpdateProcessor.getPreviewInfo(project, intention, file, editor)
    assertEquals(
        "Expected no intention preview for `${intention.getClassName()}`",
        IntentionPreviewInfo.EMPTY,
        previewInfo
    )
}

private fun IntentionAction.getClassName(): String {
    if (this is IntentionActionDelegate) return delegate.getClassName()
    if (this is LocalQuickFixAsIntentionAdapter) return familyName
    if (this is QuickFixWrapper) return fix.javaClass.simpleName
    return javaClass.simpleName
}
