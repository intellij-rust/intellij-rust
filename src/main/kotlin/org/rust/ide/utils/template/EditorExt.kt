/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils.template

import com.intellij.codeInsight.template.TemplateResultListener
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import org.rust.ide.intentions.util.macros.IntentionInMacroUtil
import org.rust.ide.intentions.util.macros.RsIntentionInsideMacroExpansionEditor
import org.rust.lang.core.psi.ext.isIntentionPreviewElement
import org.rust.openapiext.checkWriteAccessAllowed

fun Editor.buildAndRunTemplate(
    owner: PsiElement,
    elementsToReplace: Iterable<PsiElement>,
    onFinish: (() -> Unit)? = null,
) {
    if (!owner.isIntentionPreviewElement) {
        checkWriteAccessAllowed()
    }
    val tpl = newTemplateBuilder(owner)
    for (element in elementsToReplace) {
        tpl.replaceElement(element)
    }
    if (onFinish != null) {
        tpl.withListener(TemplateResultListener {
            if (it == TemplateResultListener.TemplateResult.Finished) {
                onFinish()
            }
        })
    }
    tpl.runInline()
}

fun Editor.newTemplateBuilder(context: PsiElement): RsTemplateBuilder {
    // First macros, then injections (assume that macro expansion can't contain language injections)
    val hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(IntentionInMacroUtil.unwrapEditor(this))
    val contextualPsiFile = if (this is RsIntentionInsideMacroExpansionEditor) originalFile else context.containingFile
    val hostPsiFile = InjectedLanguageManager.getInstance(context.project).getTopLevelFile(contextualPsiFile)
    return RsTemplateBuilder(hostPsiFile, this, hostEditor)
}

fun Editor.canRunTemplateFor(element: PsiElement): Boolean {
    val containingFile = element.containingFile
    if (this is RsIntentionInsideMacroExpansionEditor) {
        return containingFile == originalFile || containingFile == psiFileCopy
    }
    return PsiDocumentManager.getInstance(containingFile.project).getPsiFile(document) == containingFile
}
