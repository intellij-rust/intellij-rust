package org.rust.lang.refactoring

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.navigation.goto.RustSymbolNavigationContributor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.childOfType
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.types.util.resolvedType
import java.util.*

/**
 * This suggests names for an expression about to be bound to a local variable
 *
 * If the type is resolved suggest the first letter of the type name.
 * If its an argument to a function call, suggest the name of the argument in the function definition.
 * If its a function call suggest the name of the function and the enclosing name space (if any).
 * If a name is already bound in the local scope do not suggest it.
 */
fun suggestedNames(project: Project, expr: PsiElement): LinkedHashSet<String> {
    val names = LinkedHashSet<String>()
    nameForType(expr)?.let { names.addAll(RustNameUtil(it)) }

    val foundNames = when {
        expr.isArgument() -> RustNameUtil(nameForArgument(project, expr))
        expr is RustCallExprElement -> nameForCall(expr).flatMap(::RustNameUtil)
        else -> emptyList()
    }

    names.addAll(foundNames)

    val usedNames = findNamesInLocalScope(expr)
    names.removeAll(usedNames)

    return names
}

private fun nameForType(expr: PsiElement): String? {
    val typeString = (expr as? RustExprElement)?.resolvedType.toString()
    //doesn't really seem to do that much.
    if (typeString != "unknown" && typeString != "null") {
        return typeString.take(1)
    }

    return null
}

private fun nameForCall(expr: RustCallExprElement): List<String> {
    val pathElement = expr.expr
    if (pathElement is RustPathExprElement) {
        val path = pathElement.path

        //path.path gives us the x's out of: Xxx::yyy
        return listOf(path.identifier, path.path).filterNotNull().map(PsiElement::getText)
    }
    return listOf(pathElement.text)
}

fun nameForArgument(project: Project, expr: PsiElement): String {
    val call = expr.parentOfType<RustCallExprElement>(strict = false) ?: return ""

    val parameterIndex = call.argList.children.indexOf(expr)
    val fn = findFnImpl(project, call)

    return fn?.parameters?.parameterList?.get(parameterIndex)?.pat?.text ?: ""
}

private fun findFnImpl(project: Project, callExpr: RustCallExprElement): RustFnItemElement? {
    val navigator = RustSymbolNavigationContributor()
    val items = navigator.getItemsByName(callExpr.firstChild.text, null, project, false)

    return items.firstOrNull() as? RustFnItemElement
}

fun String.toSnakeCase(): String {
    val builder = StringBuilder()
    for (char in this.toCharArray()) {
        if (char.isUpperCase()) {
            builder
                .append('_')
                .append(char.toLowerCase())
        } else {
            builder.append(char)
        }
    }

    return builder.toString()
}

fun findNamesInLocalScope(expr: PsiElement): List<String> {
    val blockScope = expr.parentOfType<RustBlockElement>(strict = false)
    val letDecls = PsiTreeUtil.findChildrenOfType(blockScope, RustLetDeclElement::class.java)

    return letDecls.map { it.pat?.text }.filterNotNull()
}

private fun PsiElement.isArgument() = this.parent is RustArgListElement

private fun RustNameUtil(name: String) = NameUtil.getSuggestionsByName(name, "", "", false, false, false).map(String::toSnakeCase)
