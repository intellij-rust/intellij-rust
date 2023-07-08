/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.parentOfType
import org.rust.RsBundle
import org.rust.ide.annotator.getFunctionCallContext
import org.rust.ide.utils.template.buildAndRunTemplate
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.RsElementTypes.RPAREN
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.childrenWithLeaves
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getLocalVariableVisibleBindings
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.inference
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyFunctionBase
import org.rust.lang.core.types.type

class FillFunctionArgumentsFix(element: PsiElement) : RsQuickFixBase<PsiElement>(element) {
    override fun getText(): String = RsBundle.message("intention.name.fill.missing.arguments")
    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val arguments = element.parentOfType<RsValueArgumentList>(true) ?: return
        val parent = arguments.parent as? RsElement ?: return

        val requiredParameterCount = arguments.getFunctionCallContext()?.expectedParameterCount ?: return
        val parameters = getParameterTypes(parent)?.take(requiredParameterCount) ?: return

        val factory = RsPsiFactory(project)
        val builder = RsDefaultValueBuilder(parent.knownItems, parent.containingMod, factory)
        val bindings = parent.getLocalVariableVisibleBindings()

        // We are currently looking for an argument for this parameter
        var parameterIndex = 0
        // Index of the last found or inserted argument
        var argumentIndex = -1

        val argumentList = mutableListOf<RsExpr>()
        val newArgumentIndices = mutableListOf<Int>()
        val children = arguments.childrenWithLeaves.toList()
        var childIndex = 0

        while (parameterIndex < parameters.size) {
            val child = children.getOrNull(childIndex)
            childIndex++

            val isComma = child?.elementType == COMMA ||
                (child is PsiErrorElement && child.childrenWithLeaves.firstOrNull()?.elementType == COMMA)
            val atEnd = child == null || child.elementType == RPAREN
            when {
                // We are either at the end or at a comma.
                // If we are missing an argument for the current parameter, we have to insert it.
                atEnd || isComma -> {
                    if (argumentIndex < parameterIndex) {
                        argumentList.add(builder.buildFor(parameters[parameterIndex], bindings))
                        argumentIndex++
                        newArgumentIndices.add(argumentIndex)
                    }
                    parameterIndex++
                }
                child is RsExpr -> {
                    argumentList.add(child)
                    argumentIndex++
                }
            }
        }

        val newArgumentList = factory.tryCreateValueArgumentList(argumentList) ?: return
        val insertedArguments = arguments.replace(newArgumentList) as RsValueArgumentList
        val toBeChanged = newArgumentIndices.map { insertedArguments.exprList[it] }

        editor?.buildAndRunTemplate(insertedArguments, toBeChanged)
    }
}

private fun getParameterTypes(element: PsiElement): List<Ty>? {
    return when (element) {
        is RsCallExpr -> (element.expr.type as? TyFunctionBase)?.paramTypes
        is RsMethodCall -> element.inference?.getResolvedMethodType(element)?.paramTypes?.drop(1)
        else -> null
    }
}
