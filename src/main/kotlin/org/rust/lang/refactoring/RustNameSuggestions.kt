package org.rust.lang.refactoring

import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.inspections.toSnakeCase
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.types.RustUnknownType
import org.rust.lang.core.types.util.resolvedType
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
fun RustExprElement.suggestNames(): LinkedHashSet<String> {
    val names = LinkedHashSet<String>()
    nameForType(this)?.let { names.addAll(rustNameUtil(it)) }
    val parent = this.parent

    val foundNames = when {
        this.isArgument() -> rustNameUtil(nameForArgument())
        this is RustCallExprElement -> nameForCall(this).flatMap(::rustNameUtil)
        parent is RustStructExprFieldElement -> rustNameUtil(parent.identifier.text)
        else -> emptyList()
    }

    names.addAll(foundNames)

    val usedNames = findNamesInLocalScope(this)
    names.removeAll(usedNames)

    return names
}


private fun nameForType(expr: RustExprElement): String? {
    val type = expr.resolvedType

    if (type is RustUnknownType) {
        return null
    }

    return type.toString().take(1)
}

private fun nameForCall(expr: RustCallExprElement): List<String> {
    val pathElement = expr.expr
    if (pathElement is RustPathExprElement) {
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
    val call = this.parentOfType<RustCallExprElement>(strict = true) ?: return ""

    val parameterIndex = call.argList.children.indexOf(this)
    val fn = call.findFnImpl()

    return fn?.parameters?.parameterList?.get(parameterIndex)?.pat?.text ?: ""
}

private fun RustCallExprElement.findFnImpl(): RustFunctionElement? {
    val path = expr as? RustPathExprElement
    return path?.path?.reference?.resolve() as? RustFunctionElement
}


fun findNamesInLocalScope(expr: PsiElement): List<String> {
    val blockScope = expr.parentOfType<RustBlockElement>(strict = false)
    val letDecls = PsiTreeUtil.findChildrenOfType(blockScope, RustLetDeclElement::class.java)

    return letDecls.map { it.pat?.text }.filterNotNull()
}

private fun PsiElement.isArgument() = this.parent is RustArgListElement
private fun PsiElement.isStructField() = this.parent is RustStructExprFieldElement

private fun rustNameUtil(name: String) = NameUtil.getSuggestionsByName(name, "", "", false, false, false).map { it.toSnakeCase(false) }
