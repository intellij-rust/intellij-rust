/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils.template

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.template.TemplateBuilderFactory
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateEditingListener
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import org.rust.lang.core.psi.ext.isIntentionPreviewElement
import org.rust.openapiext.checkWriteAccessAllowed

fun Editor.buildAndRunTemplate(
    owner: PsiElement,
    elementsToReplace: List<SmartPsiElementPointer<out PsiElement>>,
    listener: TemplateEditingListener? = null,
) {
    if (!owner.isIntentionPreviewElement) {
        checkWriteAccessAllowed()
    }
    val tbl = newTemplateBuilder(owner) ?: return
    for (elementPointer in elementsToReplace) {
        val element = elementPointer.element ?: continue
        tbl.replaceElement(element)
    }
    if (listener != null) {
        tbl.withListener(listener)
    }
    tbl.runInline()
}

fun Editor.newTemplateBuilder(owner: PsiElement): RsTemplateBuilder? {
    val restoredOwner = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(owner) ?: return null
    val templateBuilder = TemplateBuilderFactory.getInstance().createTemplateBuilder(restoredOwner)
        as TemplateBuilderImpl
    val rootEditor = InjectedLanguageEditorUtil.getTopLevelEditor(this)
    return RsTemplateBuilder(restoredOwner, templateBuilder, this, rootEditor)
}
