/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.psi.PsiElement

// BACKCOMPAT: 2022.2. Move to `src/main/kotlin/org/rust/lang/core/psi/ext/PsiElement.kt`
val PsiElement.isIntentionPreviewElement: Boolean get() = IntentionPreviewUtils.isPreviewElement(this)

object RsIntentionPreviewUtils {
    // BACKCOMPAT: 2022.2. Inline it
    /** Performs a preview-aware write-action. Execute action immediately if preview is active; wrap into write action otherwise. */
    fun write(runnable: () -> Unit) {
        IntentionPreviewUtils.write<Throwable>(runnable)
    }
}
