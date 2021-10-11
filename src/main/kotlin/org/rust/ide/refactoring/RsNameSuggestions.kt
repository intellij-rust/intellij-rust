/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.inspections.lints.toSnakeCase
import org.rust.ide.refactoring.introduceVariable.IntroduceVariableTestmarks
import org.rust.ide.utils.CallInfo
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type
import org.rust.openapiext.hitOnFalse
import org.rust.stdext.mapNotNullToSet


class SuggestedNames(
    val default: String,
    val all: LinkedHashSet<String>
)

const val FRESHEN_LIMIT = 1000

/**
 * This suggests names for an expression about to be bound to a local variable / method parameter
 *
 * If the type is resolved and nominal, suggest it.
 * If its an argument to a function call, suggest the name of the argument in the function definition.
 * If its a function call suggest the name of the function and the enclosing name space (if any).
 * If the expression is in a struct literal (Foo {x: 5, y: 6}) suggest the tag for the expression as a name
 * If a name is already bound in the local scope do not suggest it.
 */
fun RsExpr.suggestedNames(): SuggestedNames {
    val names = type.suggestedNames()

    val parent = this.parent
    if (parent is RsValueArgumentList) {
        val call = parent.ancestorStrict<RsCallExpr>()?.let { CallInfo.resolve(it) }
        if (call != null) {
            val paramName = call.parameters
                .getOrNull(parent.exprList.indexOf(this))
                ?.pattern
            names.addName(paramName)
        }
    }

    if (this is RsCallExpr) {
        nameForCall(this).forEach { names.addName(it) }
    }

    if (parent is RsStructLiteralField) {
        names.addName(parent.identifier?.text)
    }

    return finalizeNameSelection(this, names)
}

fun Ty.suggestedNames(context: PsiElement, additionalNamesInScope: Set<String> = emptySet()): SuggestedNames {
    val names = suggestedNames()
    return finalizeNameSelection(context, names, additionalNamesInScope)
}

fun freshenName(name: String, usedNames: Set<String>): String {
    var newName = name
    var i = 1
    while (i < FRESHEN_LIMIT && usedNames.contains(newName)) {
        newName = "$name$i"
        ++i
    }
    return newName
}

private fun finalizeNameSelection(
    context: PsiElement,
    names: LinkedHashSet<String>,
    additionalNamesInScope: Set<String> = emptySet()
): SuggestedNames {
    val topName = names.firstOrNull() ?: "x"
    val usedNames = findNamesInLocalScope(context) + additionalNamesInScope

    val name = freshenName(topName, usedNames)
    names.removeAll(usedNames)
    return SuggestedNames(name, names)
}

private fun Ty.suggestedNames(): LinkedHashSet<String> {
    val names = LinkedHashSet<String>()
    when (this) {
        is TyInteger -> names.addName("i")
        is TyTypeParameter -> names.addName(name)
        is TyAdt -> names.addName(item.name)
        is TyTraitObject -> traits.forEach { names.addName(it.element.name) }
    }
    return names
}

private val uselessNames = listOf("new", "default")
private fun LinkedHashSet<String>.addName(name: String?) {
    if (name == null || name in uselessNames || !isValidRustVariableIdentifier(name)) return
    NameUtil.getSuggestionsByName(name, "", "", false, false, false)
        .filter { it !in uselessNames && IntroduceVariableTestmarks.invalidNamePart.hitOnFalse(isValidRustVariableIdentifier(it)) }
        .mapTo(this) { it.toSnakeCase(false) }
}

private fun nameForCall(expr: RsCallExpr): List<String> {
    val pathElement = expr.expr
    if (pathElement is RsPathExpr) {
        val path = pathElement.path

        //path.path.identifier gives us the x's out of: Xxx::<T>::yyy
        return listOfNotNull(path.identifier, path.path?.identifier).map(PsiElement::getText)
    }
    return listOf(pathElement.text)
}

private fun findNamesInLocalScope(expr: PsiElement): Set<String> {
    val functionScope = expr.ancestorOrSelf<RsFunction>()

    // Existing names should not be shadowed.
    // For example, see https://github.com/intellij-rust/intellij-rust/issues/2919
    return PsiTreeUtil.findChildrenOfAnyType(functionScope, RsPatBinding::class.java, RsPath::class.java)
        .mapNotNullToSet {
            when (it) {
                is RsPath -> it.referenceName
                is RsPatBinding -> it.name
                else -> null
            }
        }
}
