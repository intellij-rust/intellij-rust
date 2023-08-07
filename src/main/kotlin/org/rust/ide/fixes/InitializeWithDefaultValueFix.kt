/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.ide.utils.template.buildAndRunTemplate
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.getLocalVariableVisibleBindings
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.declaration
import org.rust.lang.core.types.type

class InitializeWithDefaultValueFix(element: RsElement) : RsQuickFixBase<RsElement>(element) {
    override fun getText() = RsBundle.message("intention.name.initialize.with.default.value")
    override fun getFamilyName() = name

    override fun invoke(project: Project, editor: Editor?, element: RsElement) {
        val variable = element.ancestorOrSelf<RsExpr>() ?: return
        val patBinding = variable.declaration as? RsPatBinding ?: return
        val declaration = patBinding.ancestorOrSelf<RsLetDecl>() ?: return
        val semicolon = declaration.semicolon ?: return
        val psiFactory = RsPsiFactory(project)
        val initExpr = RsDefaultValueBuilder(declaration.knownItems, declaration.containingMod, psiFactory, true)
            .buildFor(patBinding.type, element.getLocalVariableVisibleBindings())

        if (declaration.eq == null) {
            declaration.addBefore(psiFactory.createEq(), semicolon)
        }
        val addedInitExpr = declaration.addBefore(initExpr, semicolon)
        editor?.buildAndRunTemplate(declaration, listOf(addedInitExpr))
    }

    companion object {
        fun createIfCompatible(element: RsElement): InitializeWithDefaultValueFix? {
            val variable = element.ancestorOrSelf<RsExpr>() ?: return null
            val patBinding = variable.declaration as? RsPatBinding ?: return null
            val declaration = patBinding.ancestorOrSelf<RsLetDecl>()
            if (declaration?.pat !is RsPatIdent || declaration.semicolon == null) return null
            return InitializeWithDefaultValueFix(element)
        }
    }
}
