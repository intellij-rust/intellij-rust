package org.rust.lang.refactoring

import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.NameUtil
import org.rust.ide.navigation.goto.RustSymbolNavigationContributor
import org.rust.lang.core.psi.RustArgListElement
import org.rust.lang.core.psi.RustCallExprElement
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.RustFnItemElement
import org.rust.lang.core.psi.util.parentOfType
import java.util.*

fun suggestedNames(project: Project, expr: RustExprElement): LinkedHashSet<String> {
    val names = LinkedHashSet<String>()

    if (expr.isArgument()) {
        val name = nameForArgument(project, expr)
        val suggestionsByName = NameUtil.getSuggestionsByName(name, "", "", false, false, false).map(String::toSnakeCase)
        names.addAll(suggestionsByName)
    }

    return names
}


fun nameForArgument(project: Project, expr: RustExprElement): String {
    val call = expr.parentOfType<RustCallExprElement>(strict = false) ?: return ""

    val parameterIndex = call.argList.children.indexOf(expr)

    val navigator = RustSymbolNavigationContributor()
    val items = navigator.getItemsByName(call.firstChild.text, null, project, false)
    val fn = items[0] as RustFnItemElement

    return fn.parameters?.parameterList?.get(parameterIndex)?.pat?.text ?: ""
}

fun String.toSnakeCase(): String {
    val builder = StringBuilder()
    for (char in this.toCharArray()) {
        if(char.isUpperCase()) {
            builder
                .append('_')
                .append(char.toLowerCase())
        } else {
            builder.append(char)
        }
    }

    return builder.toString()
}

fun RustExprElement.isArgument() = this.parentOfType<RustArgListElement>(strict = false) != null
