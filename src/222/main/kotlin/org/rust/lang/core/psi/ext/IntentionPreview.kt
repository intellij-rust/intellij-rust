/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiElement

val PsiElement.isIntentionPreviewElement: Boolean get() = false

object RsIntentionPreviewUtils {
    fun write(runnable: () -> Unit) {
        runWriteAction(runnable)
    }
}
