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
import com.intellij.psi.util.parentOfType
import org.rust.ide.annotator.expectedParamsCount
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.selfType
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.type
import org.rust.openapiext.buildAndRunTemplate
import org.rust.openapiext.createSmartPointer

class FillFunctionArgumentsFix(element: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getText(): String = "Fill missing arguments"
    override fun getFamilyName(): String = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val arguments = startElement.parentOfType<RsValueArgumentList>(true) ?: return
        val parent = arguments.parent as? RsElement ?: return

        val parameters = getParameterTypes(parent) ?: return
        val requiredParameterCount = when (parent) {
            is RsCallExpr -> parent.expectedParamsCount()?.first
            is RsMethodCall -> parent.expectedParamsCount()?.first
            else -> null
        } ?: return

        val actualArgumentCount = arguments.exprList.size
        val missingCount = requiredParameterCount - actualArgumentCount
        if (missingCount < 1) return

        val factory = RsPsiFactory(project)
        val builder = RsDefaultValueBuilder(parent.knownItems, parent.containingMod, factory)
        val bindings = RsDefaultValueBuilder.getVisibleBindings(parent)

        val missingArguments = parameters.drop(actualArgumentCount).take(missingCount).map {
            builder.buildFor(it ?: return@map factory.createExpression("()"), bindings)
        }
        val newArguments = factory.tryCreateValueArgumentList(arguments.exprList + missingArguments) ?: return
        val inserted = arguments.replace(newArguments) as RsValueArgumentList

        val insertedArguments = inserted.exprList.drop(actualArgumentCount)
        editor?.buildAndRunTemplate(inserted, insertedArguments.map { it.createSmartPointer() })
    }
}

private fun getParameterTypes(element: PsiElement): List<Ty?>? {
    return when (element) {
        is RsCallExpr -> {
            when (val target = (element.expr as? RsPathExpr)?.path?.reference?.resolve()) {
                is RsFunction -> {
                    val valueParameters = target.valueParameters.map { it.typeReference?.type }
                    if (target.isMethod) {
                        listOf(getSelfType(target, element)) + valueParameters
                    }
                    else {
                        valueParameters
                    }
                }
                is RsFieldsOwner -> target.tupleFields?.tupleFieldDeclList?.map { it.typeReference.type }
                else -> null
            }
        }
        is RsMethodCall -> (element.reference.resolve() as? RsFunction)?.valueParameterList?.valueParameterList?.map {
            it.typeReference?.type
        }
        else -> null
    }
}

fun getSelfType(function: RsFunction, call: RsCallExpr): Ty? {
    val selfParameter = function.selfParameter ?: return null
    if (selfParameter.typeReference?.type != null) {
        return selfParameter.typeReference?.type
    }
    val owner = when (val owner = function.owner) {
        is RsAbstractableOwner.Impl -> owner.impl.selfType
        is RsAbstractableOwner.Trait -> {
            val pathExpr = call.expr as? RsPathExpr
            val typeOwner = pathExpr?.path?.qualifier?.reference?.resolve() as? RsTypeDeclarationElement
            typeOwner?.declaredType ?: owner.trait.selfType
        }
        else -> return null
    }
    return when {
        selfParameter.isRef -> TyReference(owner, selfParameter.mutability)
        else -> owner
    }
}
