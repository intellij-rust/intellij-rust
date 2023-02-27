/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import org.rust.ide.intentions.util.macros.IntentionInMacroUtil
import org.rust.ide.intentions.util.macros.RsIntentionInsideMacroExpansionEditor

fun Editor.moveCaretToOffset(context: PsiElement, absoluteOffsetInFile: Int) {
    val targetEditor = if (this is RsIntentionInsideMacroExpansionEditor && originalFile == context.containingFile) {
        originalEditor
    } else {
        this
    }
    targetEditor.caretModel.moveToOffset(absoluteOffsetInFile)
}

fun Editor.setSelection(context: PsiElement, startOffset: Int, endOffset: Int) {
    val targetEditor = if (this is RsIntentionInsideMacroExpansionEditor && originalFile == context.containingFile) {
        originalEditor
    } else {
        this
    }
    targetEditor.selectionModel.setSelection(startOffset, endOffset)
}

@Suppress("UnstableApiUsage")
fun Editor.showErrorHint(@NlsContexts.HintText text: String, @HintManager.PositionFlags position: Short) {
    val editor = IntentionInMacroUtil.unwrapEditor(this)
    HintManager.getInstance().showErrorHint(editor, text, position)
}

@Suppress("UnstableApiUsage")
fun Editor.showErrorHint(@NlsContexts.HintText text: String) {
    val editor = IntentionInMacroUtil.unwrapEditor(this)
    HintManager.getInstance().showErrorHint(editor, text)
}
