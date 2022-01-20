/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.rust.ide.inspections.getTypeArgumentsAndDeclaration
import org.rust.ide.utils.template.buildAndRunTemplate
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.RsElementTypes.LT
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTypeArgumentList
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.createSmartPointer

class AddGenericArguments(
    private val declaration: SmartPsiElementPointer<RsGenericDeclaration>,
    element: RsElement
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getText(): String = "Add missing $argumentsName"
    override fun getFamilyName() = "Add missing generic arguments"

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val element = startElement as? RsElement ?: return
        val inserted = insertGenericArgumentsIfNeeded(element) ?: return
        editor?.buildAndRunTemplate(element, inserted.map { it.createSmartPointer() })
    }

    private val argumentsName: String
        get() {
            val element = declaration.element ?: return "generic arguments"
            return when {
                element.typeParameters.isNotEmpty() && element.constParameters.isNotEmpty() -> "generic arguments"
                element.typeParameters.isNotEmpty() -> "type arguments"
                element.constParameters.isNotEmpty() -> "const arguments"
                else -> "generic arguments"
            }
        }
}

/**
 * Inserts type arguments if they are needed and returns a list of inserted generic arguments.
 */
fun insertGenericArgumentsIfNeeded(pathOrMethodCall: RsElement): List<RsElement>? {
    val (typeArguments, declaration) = getTypeArgumentsAndDeclaration(pathOrMethodCall) ?: return null

    val requiredParameters = declaration.requiredGenericParameters
    if (requiredParameters.isEmpty()) return null

    val argumentCount = (typeArguments?.typeReferenceList?.size ?: 0) + (typeArguments?.exprList?.size ?: 0)
    if (argumentCount >= requiredParameters.size) return null

    val factory = RsPsiFactory(pathOrMethodCall.project)
    val missingParams = requiredParameters.drop(argumentCount).map { it.name ?: "_" }

    val replaced = if (typeArguments != null) {
        var anchor = with(typeArguments) {
            (lifetimeList + typeReferenceList + exprList).maxByOrNull { it.startOffset } ?: lt
        }
        val nextSibling = anchor.getNextNonCommentSibling()
        val addCommaAfter = nextSibling?.isComma == true
        if (addCommaAfter && nextSibling != null) {
            anchor = nextSibling
        }

        for (type in missingParams) {
            if (anchor.elementType != LT && !anchor.isComma) {
                anchor = typeArguments.addAfter(factory.createComma(), anchor)
            }
            anchor = typeArguments.addAfter(factory.createType(type), anchor)
        }

        if (addCommaAfter) {
            typeArguments.addAfter(factory.createComma(), anchor)
        }

        typeArguments
    } else {
        val newArgumentList = factory.createTypeArgumentList(missingParams)

        // this can only happen for type references (base types/trait refs)
        if (pathOrMethodCall !is RsPath) return null
        pathOrMethodCall.addAfter(newArgumentList, pathOrMethodCall.identifier) as RsTypeArgumentList
    }
    return (replaced.typeReferenceList + replaced.exprList).sortedBy { it.startOffset }.drop(argumentCount)
}

private val PsiElement.isComma: Boolean
    get() = elementType == COMMA
