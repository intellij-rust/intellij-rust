/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsLifetime
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.psi.ext.RsNameIdentifierOwner
import org.rust.lang.core.psi.ext.ancestorOrSelf

class CreateLifetimeParameterFromUsageFix(lifetime: RsLifetime) : LocalQuickFixAndIntentionActionOnPsiElement(lifetime) {

    override fun getFamilyName(): String = "Create lifetime parameter"
    override fun getText(): String = familyName

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val context = gatherContext(startElement) ?: return
        insertLifetime(context.declaration, project, context.lifetime)
    }

    private fun insertLifetime(declaration: RsGenericDeclaration, project: Project, startElement: RsLifetime) {
        val originalParams = declaration.typeParameterList
        val factory = RsPsiFactory(project)
        if (originalParams != null) {
            val parameters = mutableListOf<RsElement>()
            parameters.addAll(originalParams.lifetimeParameterList)
            parameters.add(startElement)
            parameters.addAll(originalParams.typeParameterList)
            val parameterList = factory.createTypeParameterList(parameters.joinToString(", ") { it.text })
            originalParams.replace(parameterList)
        } else {
            val parameterList = factory.createTypeParameterList(startElement.text)
            if (declaration !is RsNameIdentifierOwner) return
            val nameIdentifier = declaration.nameIdentifier
            if (nameIdentifier != null) {
                declaration.addAfter(parameterList, nameIdentifier)
            }
        }
    }

    companion object {
        fun isAvailable(lifetime: RsLifetime): Boolean {
            return gatherContext(lifetime) != null
        }
    }
}

private class Context(
    val lifetime: RsLifetime,
    val declaration: RsGenericDeclaration
)

private fun gatherContext(element: PsiElement): Context? {
    if (element !is RsLifetime) return null
    val genericDeclaration = element.ancestorOrSelf<RsGenericDeclaration>()
    if (genericDeclaration !is RsNameIdentifierOwner) return null
    return Context(element, genericDeclaration)
}
