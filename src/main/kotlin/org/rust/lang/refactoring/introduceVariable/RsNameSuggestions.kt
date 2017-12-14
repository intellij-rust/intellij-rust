/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.introduceVariable

import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.NameUtil
import org.rust.ide.inspections.toSnakeCase
import org.rust.ide.utils.CallInfo
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.resolve.hitOnFalse
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.core.types.ty.TyStructOrEnumBase
import org.rust.lang.core.types.ty.TyTraitObject
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.type
import org.rust.lang.refactoring.isValidRustVariableIdentifier


class SuggestedNames(
    val default: String,
    val all: LinkedHashSet<String>
)

/**
 * This suggests names for an expression about to be bound to a local variable
 *
 * If the type is resolved and nominal, suggest it.
 * If its an argument to a function call, suggest the name of the argument in the function definition.
 * If its a function call suggest the name of the function and the enclosing name space (if any).
 * If the expression is in a struct literal (Foo {x: 5, y: 6}) suggest the tag for the expression as a name
 * If a name is already bound in the local scope do not suggest it.
 */
fun RsExpr.suggestedNames(): SuggestedNames {
    val names = LinkedHashSet<String>()
    val type = type
    when (type) {
        is TyInteger -> names.addName("i")
        is TyTypeParameter -> names.addName(type.name)
        is TyStructOrEnumBase -> names.addName(type.item.name)
        is TyTraitObject -> names.addName(type.trait.element.name)
    }

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
        names.addName(parent.identifier.text)
    }

    val usedNames = findNamesInLocalScope(this)
    names.removeAll(usedNames)

    return SuggestedNames(names.firstOrNull() ?: "x", names)
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
        return listOf(path.identifier, path.path?.identifier)
            .filterNotNull().map(PsiElement::getText)
    }
    return listOf(pathElement.text)
}

private fun findNamesInLocalScope(expr: PsiElement): List<String> {
    val blockScope = expr.ancestorOrSelf<RsBlock>()
    val letDecls = blockScope?.descendantsOfType<RsLetDecl>().orEmpty()

    return letDecls.mapNotNull { it.pat?.text }
}
