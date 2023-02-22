/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement

@Suppress("UNUSED_PARAMETER")
fun Editor.moveCaretToOffset(context: PsiElement, absoluteOffsetInFile: Int) {
    caretModel.moveToOffset(absoluteOffsetInFile)
}

@Suppress("UNUSED_PARAMETER")
fun Editor.setSelection(context: PsiElement, startOffset: Int, endOffset: Int) {
    selectionModel.setSelection(startOffset, endOffset)
}

@Suppress("UnstableApiUsage")
fun Editor.showErrorHint(@NlsContexts.HintText text: String, @HintManager.PositionFlags position: Short) {
    HintManager.getInstance().showErrorHint(this, text, position)
}

@Suppress("UnstableApiUsage")
fun Editor.showErrorHint(@NlsContexts.HintText text: String) {
    HintManager.getInstance().showErrorHint(this, text)
}
