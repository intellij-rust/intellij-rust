/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.template.TemplateBuilderFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

fun <T : PsiElement, E : PsiElement> Editor.buildAndRunTemplate(
    owner: T,
    elementsToReplace: List<SmartPsiElementPointer<E>>
) {
    checkWriteAccessAllowed()
    val restoredOwner = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(owner)
    val templateBuilder = TemplateBuilderFactory.getInstance().createTemplateBuilder(restoredOwner)
    for (elementPointer in elementsToReplace) {
        val element = elementPointer.element ?: continue
        templateBuilder.replaceElement(element, element.text)
    }
    templateBuilder.run(this, true)
}
