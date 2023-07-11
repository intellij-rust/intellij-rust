/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.RsBundle
import org.rust.lang.core.completion.safeGetOriginalOrSelf
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*


/**
 * Fix that removes a parameter and all its usages at call sites.
 */
class RemoveParameterFix(
    binding: RsPatBinding,
    private val bindingName: String
) : RsQuickFixBase<RsPatBinding>(binding) {
    override fun getText() = RsBundle.message("intention.name.remove.parameter", bindingName)
    override fun getFamilyName() = RsBundle.message("intention.family.name.remove.parameter")

    override fun invoke(project: Project, editor: Editor?, element: RsPatBinding) {
        val patIdent = element.topLevelPattern as? RsPatIdent ?: return
        val parameter = patIdent.parent as? RsValueParameter ?: return
        val function = parameter.parentOfType<RsFunction>() ?: return
        val originalFunction = function.safeGetOriginalOrSelf()
        val calls = if (function.isIntentionPreviewElement) {
            emptyList()
        } else {
            (originalFunction.findFunctionCalls() + originalFunction.findMethodCalls()).toList()
        }

        val parameterIndex = function.valueParameterList?.valueParameterList?.indexOf(parameter) ?: -1
        if (parameterIndex == -1) return

        parameter.deleteWithSurroundingCommaAndWhitespace()
        removeArguments(function, calls, parameterIndex)
    }
}

private fun removeArguments(function: RsFunction, calls: List<PsiElement>, parameterIndex: Int) {
    for (call in calls) {
        val arguments = when (call) {
            is RsCallExpr -> call.valueArgumentList
            is RsMethodCall -> call.valueArgumentList
            else -> continue
        }
        val isMethod = function.hasSelfParameters
        val argumentIndex = when {
            isMethod && call is RsCallExpr -> parameterIndex + 1 // UFCS
            else -> parameterIndex
        }
        arguments.exprList.getOrNull(argumentIndex)?.deleteWithSurroundingCommaAndWhitespace()
    }
}
