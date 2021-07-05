/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.annotator.getFunctionCallContext
import org.rust.ide.presentation.render
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.refactoring.changeSignature.*
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.isMethod
import org.rust.lang.core.psi.ext.valueParameters
import org.rust.lang.core.types.type
import org.rust.stdext.numberSuffix

class ChangeFunctionParameterTypeFix private constructor(
    expr: RsExpr,
    val index: Int,
    function: RsFunction
) : LocalQuickFixAndIntentionActionOnPsiElement(expr), PriorityAction {
    private val fixText: String = run {
        val callableType = if (function.isMethod) "method" else "function"
        val name = function.name
        val parameterPat = function.valueParameters.getOrNull(index)?.pat
        val parameterText = if (parameterPat is RsPatIdent) {
            "parameter `${parameterPat.patBinding.name}`"
        } else {
            val num = index + 1
            "`$num${numberSuffix(num)}` parameter"
        }

        "Change type of $parameterText of $callableType `$name` to `${expr.type.render()}`"
    }

    override fun getText(): String = fixText
    override fun getFamilyName(): String = "Change parameter type"

    override fun startInWriteAction(): Boolean = false

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.LOW

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val expr = startElement as? RsExpr ?: return
        val args = expr.parent as? RsValueArgumentList ?: return
        val context = when (val parent = args.parent) {
            is RsCallExpr -> parent.getFunctionCallContext()
            is RsMethodCall -> parent.getFunctionCallContext()
            else -> null
        } ?: return
        val function = context.function ?: return

        val config = RsChangeFunctionSignatureConfig.create(function)
        val factory = RsPsiFactory(project)

        if (index < config.parameters.size) {
            val original = config.parameters[index]
            val typeText = expr.type.renderInsertionSafe(
                skipUnchangedDefaultTypeArguments = true,
                useAliasNames = true
            )
            val fragment = RsTypeReferenceCodeFragment(project, typeText, context = args)
            val typeReference = fragment.typeReference ?: factory.createType("()")
            config.parameters[index] = Parameter(
                factory,
                original.patText,
                ParameterProperty.fromItem(typeReference),
                original.index
            )
            config.additionalTypesToImport.add(expr.type)
        }

        runChangeSignatureRefactoring(config)
    }

    companion object {
        fun createIfCompatible(element: RsExpr): ChangeFunctionParameterTypeFix? {
            val args = element.parent as? RsValueArgumentList ?: return null
            val parent = args.parent
            val context = when (parent) {
                is RsCallExpr -> parent.getFunctionCallContext()
                is RsMethodCall -> parent.getFunctionCallContext()
                else -> null
            }
            val function = context?.function ?: return null
            if (function.containingCrate?.origin != PackageOrigin.WORKSPACE) return null
            if (!RsChangeSignatureHandler.isChangeSignatureAvailable(function)) return null

            val isUFCS = function.isMethod && parent is RsCallExpr
            var index = args.exprList.indexOf(element)

            if (isUFCS) {
                // Ignore self parameters of UFCS calls
                if (index == 0) return null
                index -= 1
            }

            return ChangeFunctionParameterTypeFix(element, index, context.function)
        }
    }
}
