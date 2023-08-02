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
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.rust.stdext.RsResult

fun CodeInsightTestFixture.checkPreviewAndLaunchAction(
    intention: IntentionAction,
    preview: String?,
    isWrappingActive: Boolean,
): DeferredPreviewCheck {
    val actualPreviewResult = try {
        RsResult.Ok(getIntentionPreviewText(intention))
    } catch (e: Throwable) {
        RsResult.Err(e)
    }

    launchAction(intention)

    val actualPreview = when (actualPreviewResult) {
        is RsResult.Ok -> actualPreviewResult.ok ?: return DeferredPreviewCheck.FailNoPreview(intention)
        is RsResult.Err -> return DeferredPreviewCheck.PreviewFailed(actualPreviewResult.err)
    }

    //TODO support custom preview with test wrapping
    if (isWrappingActive && preview != null) {
        return DeferredPreviewCheck.IgnorePreview
    }

    val expectedPreview = preview?.trimIndent() ?: file.text
    return DeferredPreviewCheck.CheckHasPreview(intention, expectedPreview, actualPreview)
}

fun CodeInsightTestFixture.checkNoPreview(intention: IntentionAction): DeferredPreviewCheck {
    val previewInfo = IntentionPreviewPopupUpdateProcessor.getPreviewInfo(project, intention, file, editor)
    return DeferredPreviewCheck.CheckNoPreview(intention, previewInfo)
}

sealed interface DeferredPreviewCheck {
    fun checkPreview()

    object IgnorePreview : DeferredPreviewCheck {
        override fun checkPreview() {}
    }

    class PreviewFailed(
        private val throwable: Throwable,
    ) : DeferredPreviewCheck {
        override fun checkPreview() {
            throw throwable
        }
    }

    class FailNoPreview(
        private val intention: IntentionAction,
    ) : DeferredPreviewCheck {
        override fun checkPreview() {
            Assert.fail(
                "No intention preview for `${intention.getClassName()}`. " +
                    "Either support preview or pass `previewExpected = false`"
            )
        }
    }

    class CheckHasPreview(
        private val intention: IntentionAction,
        private val expectedPreview: String?,
        private val actualPreview: String,
    ) : DeferredPreviewCheck {
        override fun checkPreview() {
            assertEquals(
                "Intention `${intention.getClassName()}` produced different result when invoked in preview mode",
                expectedPreview,
                actualPreview
            )
        }
    }

    class CheckNoPreview(
        private val intention: IntentionAction,
        private val previewInfo: IntentionPreviewInfo
    ) : DeferredPreviewCheck {
        override fun checkPreview() {
            assertEquals(
                "Expected no intention preview for `${intention.getClassName()}`",
                IntentionPreviewInfo.EMPTY,
                previewInfo
            )
        }
    }
}

private fun IntentionAction.getClassName(): String {
    if (this is IntentionActionDelegate) return delegate.getClassName()
    if (this is LocalQuickFixAsIntentionAdapter) return familyName
    if (this is QuickFixWrapper) return fix.javaClass.simpleName
    return javaClass.simpleName
}
