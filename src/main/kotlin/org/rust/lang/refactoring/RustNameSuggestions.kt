    package org.rust.lang.refactoring

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.NameUtil
import org.rust.ide.navigation.goto.RustSymbolNavigationContributor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.types.util.resolvedType
import java.util.*

fun suggestedNames(project: Project, expr: PsiElement): LinkedHashSet<String> {
    val names = LinkedHashSet<String>()


    nameForType(expr)?.let { names.addAll(RustNameUtil(it)) }

    if (expr.isArgument()) {
        val name = nameForArgument(project, expr)
        val suggestionsByName = RustNameUtil(name)
        names.addAll(suggestionsByName)
    } else if (expr is RustCallExprElement) {
        names.addAll(nameForCall(expr).flatMap(::RustNameUtil))
    }

    return names
}

fun nameForType(expr: PsiElement): String? {
    val typeString = (expr as? RustExprElement)?.resolvedType.toString()
    //doesn't really seem to do that much.
    if (typeString != "unkown" && typeString != "null") {
        return typeString.take(1)
    }

    return null
}

fun nameForCall(expr: RustCallExprElement): List<String> {
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

fun findFnImpl(project: Project, callExpr: RustCallExprElement): RustFnItemElement? {
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

fun PsiElement.isArgument() = this.parent is RustArgListElement

fun RustNameUtil(name: String) = NameUtil.getSuggestionsByName(name, "", "", false, false, false).map(String::toSnakeCase)
