/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring

import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.inspections.toSnakeCase
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.valueParameters
import org.rust.lang.core.psi.ext.parentOfType
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import java.util.*

/**
 * This suggests names for an expression about to be bound to a local variable
 *
 * If the type is resolved suggest the first letter of the type name.
 * If its an argument to a function call, suggest the name of the argument in the function definition.
 * If its a function call suggest the name of the function and the enclosing name space (if any).
 * If the expression is in a struct literal (Foo {x: 5, y: 6}) suggest the tag for the expression as a name
 * If a name is already bound in the local scope do not suggest it.
 */
fun RsExpr.suggestNames(): LinkedHashSet<String> {
    val names = LinkedHashSet<String>()
    nameForType(this)?.let { names.addAll(rustNameUtil(it)) }
    val parent = this.parent

    val foundNames = when {
        this.isArgument() -> rustNameUtil(nameForArgument())
        this is RsCallExpr -> nameForCall(this).flatMap(::rustNameUtil)
        parent is RsStructLiteralField -> rustNameUtil(parent.identifier.text)
        else -> emptyList()
    }

    names.addAll(foundNames)

    val usedNames = findNamesInLocalScope(this)
    names.removeAll(usedNames)

    return names
}


private fun nameForType(expr: RsExpr): String? {
    val type = expr.type

    if (type is TyUnknown) {
        return null
    }

    return type.toString().take(1)
}

private fun nameForCall(expr: RsCallExpr): List<String> {
    val pathElement = expr.expr
    if (pathElement is RsPathExpr) {
        val path = pathElement.path

        //path.path.identifier gives us the x's out of: Xxx::<T>::yyy
        return listOf(path.identifier, path.path?.identifier).filterNotNull().map(PsiElement::getText).reverseNew()
    }
    return listOf(pathElement.text)
}

private fun List<String>.reverseNew() = if (this.firstOrNull() == "new") {
    this.reversed()
} else {
    this
}

fun PsiElement.nameForArgument(): String {
    val call = this.parentOfType<RsCallExpr>(strict = true) ?: return ""

    val parameterIndex = call.valueArgumentList.children.indexOf(this)
    val fn = call.findFnImpl()

    return fn?.valueParameters?.get(parameterIndex)?.pat?.text ?: ""
}

private fun RsCallExpr.findFnImpl(): RsFunction? {
    val path = expr as? RsPathExpr
    return path?.path?.reference?.resolve() as? RsFunction
}


fun findNamesInLocalScope(expr: PsiElement): List<String> {
    val blockScope = expr.parentOfType<RsBlock>(strict = false)
    val letDecls = PsiTreeUtil.findChildrenOfType(blockScope, RsLetDecl::class.java)

    return letDecls.map { it.pat?.text }.filterNotNull()
}

private fun PsiElement.isArgument() = this.parent is RsValueArgumentList

private fun rustNameUtil(name: String) = NameUtil.getSuggestionsByName(name, "", "", false, false, false).map { it.toSnakeCase(false) }
