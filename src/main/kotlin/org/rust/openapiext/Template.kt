/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.template.TemplateBuilderFactory
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateEditingListener
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.rust.lang.core.psi.ext.startOffset

fun Editor.buildAndRunTemplate(
    owner: PsiElement,
    elementsToReplace: List<SmartPsiElementPointer<out PsiElement>>,
    listener: TemplateEditingListener? = null,
) {
    checkWriteAccessAllowed()
    val restoredOwner = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(owner) ?: return
    val templateBuilder = TemplateBuilderFactory.getInstance().createTemplateBuilder(restoredOwner)
    for (elementPointer in elementsToReplace) {
        val element = elementPointer.element ?: continue
        templateBuilder.replaceElement(element, element.text)
    }
    if (listener == null) {
        templateBuilder.run(this, true)
    } else {
        val templateBuilderImpl = templateBuilder as TemplateBuilderImpl
        // From TemplateBuilderImpl.run()
        val template = templateBuilderImpl.buildInlineTemplate()
        caretModel.moveToOffset(restoredOwner.startOffset)
        TemplateManager.getInstance(owner.project).startTemplate(this, template, listener)
    }
}
