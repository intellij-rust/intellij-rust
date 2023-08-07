/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.RsLifetime
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.*

class CreateLifetimeParameterFromUsageFix(lifetime: RsLifetime) : RsQuickFixBase<RsLifetime>(lifetime) {

    override fun getFamilyName(): String = RsBundle.message("intention.family.name.create.lifetime.parameter")
    override fun getText(): String = familyName

    override fun invoke(project: Project, editor: Editor?, element: RsLifetime) {
        val context = gatherContext(element) ?: return
        insertLifetime(context.declaration, project, context.lifetime)
    }

    private fun insertLifetime(declaration: RsGenericDeclaration, project: Project, startElement: RsLifetime) {
        val originalParams = declaration.typeParameterList
        val factory = RsPsiFactory(project)
        if (originalParams != null) {
            val parameters = mutableListOf<RsElement>()
            parameters.addAll(originalParams.lifetimeParameterList)
            parameters.add(startElement)
            parameters.addAll(originalParams.getGenericParameters(includeLifetimes = false))
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
        fun tryCreate(lifetime: RsLifetime): CreateLifetimeParameterFromUsageFix? =
            if (gatherContext(lifetime) != null) CreateLifetimeParameterFromUsageFix(lifetime) else null
    }
}

private class Context(
    val lifetime: RsLifetime,
    val declaration: RsGenericDeclaration
)

private fun gatherContext(element: RsLifetime): Context? {
    val genericDeclaration = element.ancestorOrSelf<RsGenericDeclaration>()
    if (genericDeclaration !is RsNameIdentifierOwner) return null
    return Context(element, genericDeclaration)
}
