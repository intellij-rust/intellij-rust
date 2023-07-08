/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.rust.RsBundle
import org.rust.ide.inspections.getTypeArgumentsAndDeclaration
import org.rust.ide.utils.template.buildAndRunTemplate
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.RsElementTypes.LT
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTypeArgumentList
import org.rust.lang.core.psi.ext.*

class AddGenericArguments(
    @SafeFieldForPreview
    private val declaration: SmartPsiElementPointer<RsGenericDeclaration>,
    element: RsMethodOrPath
) : RsQuickFixBase<RsMethodOrPath>(element) {
    override fun getText(): String = RsBundle.message("intention.name.add.missing", argumentsName)
    override fun getFamilyName() = RsBundle.message("intention.family.name.add.missing.generic.arguments")

    override fun invoke(project: Project, editor: Editor?, element: RsMethodOrPath) {
        val inserted = insertGenericArgumentsIfNeeded(element) ?: return
        editor?.buildAndRunTemplate(element, inserted.map { it })
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
fun insertGenericArgumentsIfNeeded(pathOrMethodCall: RsMethodOrPath): List<RsElement>? {
    val (typeArgumentsExisting, declaration) = getTypeArgumentsAndDeclaration(pathOrMethodCall) ?: return null

    val requiredParameters = declaration.requiredGenericParameters
    if (requiredParameters.isEmpty()) return null

    val factory = RsPsiFactory(pathOrMethodCall.project)
    val typeArguments = typeArgumentsExisting ?: run {
        // this can only happen for type references (base types/trait refs)
        if (pathOrMethodCall !is RsPath) return null
        pathOrMethodCall.addEmptyTypeArguments(factory)
    }

    val argumentCount = typeArguments.typeReferenceList.size + typeArguments.exprList.size
    if (argumentCount >= requiredParameters.size) return null

    val missingParams = requiredParameters.drop(argumentCount).map { it.name ?: "_" }
    val lastArgument = with(typeArguments) {
        (lifetimeList + typeReferenceList + exprList).maxByOrNull { it.startOffset } ?: lt
    }
    return typeArguments.addElements(missingParams.map(factory::createType), lastArgument, factory)
}

fun RsPath.addEmptyTypeArguments(factory: RsPsiFactory): RsTypeArgumentList {
    val list = factory.createTypeArgumentList(emptyList())
    return addAfter(list, identifier) as RsTypeArgumentList
}

/** Result on `Foo<A>` and `listOf(B, C)` is `Foo<A, B, C>` */
fun <T : RsElement> RsTypeArgumentList.addElements(
    elements: List<T>,
    anchor: PsiElement,
    factory: RsPsiFactory
): List<T> {
    val nextSibling = anchor.getNextNonCommentSibling() ?: return emptyList()
    val addCommaAfter = nextSibling.isComma

    @Suppress("NAME_SHADOWING")
    var anchor = if (addCommaAfter) nextSibling else anchor

    val added = mutableListOf<T>()
    for (element in elements) {
        if (anchor.elementType != LT && !anchor.isComma) {
            anchor = addAfter(factory.createComma(), anchor)
        }
        anchor = addAfter(element, anchor)
        @Suppress("UNCHECKED_CAST")
        added += anchor as T
    }
    if (addCommaAfter) {
        addAfter(factory.createComma(), anchor)
    }
    return added
}

private val PsiElement.isComma: Boolean
    get() = elementType == COMMA
