package org.rust.lang.refactoring

import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.NameUtil
import org.rust.ide.navigation.goto.RustSymbolNavigationContributor
import org.rust.lang.core.psi.RustArgListElement
import org.rust.lang.core.psi.RustCallExprElement
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.RustFnItemElement
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.types.util.resolvedType
import java.util.*

fun suggestedNames(project: Project, expr: RustExprElement): LinkedHashSet<String> {
    val names = LinkedHashSet<String>()

    val typeString = expr.resolvedType.toString()
    //doesn't really seem to do that much.
    if (typeString != "unkown") {
        names.addAll(RustNameUtil(typeString.take(1)))
    }

    if (expr.isArgument()) {
        val name = nameForArgument(project, expr)
        val suggestionsByName = RustNameUtil(name)
        names.addAll(suggestionsByName)
    } else if (expr is RustCallExprElement) {
        names.addAll(RustNameUtil(expr.expr.text))
    }

    return names
}

fun nameForArgument(project: Project, expr: RustExprElement): String {
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

fun RustExprElement.isArgument() = this.parent is RustArgListElement

fun RustNameUtil(name: String) = NameUtil.getSuggestionsByName(name, "", "", false, false, false).map(String::toSnakeCase)
