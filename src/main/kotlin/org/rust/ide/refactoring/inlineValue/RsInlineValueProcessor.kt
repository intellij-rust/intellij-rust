/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inlineValue

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.rust.RsBundle
import org.rust.ide.refactoring.RsInlineUsageViewDescriptor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.isShorthand
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.stdext.capitalized

class RsInlineValueProcessor(
    private val project: Project,
    private val context: InlineValueContext,
    private val mode: InlineValueMode
) : BaseRefactoringProcessor(project) {
    override fun findUsages(): Array<UsageInfo> {
        if (mode is InlineValueMode.InlineThisOnly && context.reference != null) {
            return arrayOf(UsageInfo(context.reference))
        }

        val projectScope = GlobalSearchScope.projectScope(project)
        val usages = mutableListOf<PsiReference>()
        usages.addAll(ReferencesSearch.search(context.element, projectScope).findAll())

        return usages.map(::UsageInfo).toTypedArray()
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val factory = RsPsiFactory(project)
        usages.asIterable().forEach loop@{
            val reference = it.reference as? RsReference ?: return@loop
            when (val element = reference.element) {
                is RsStructLiteralField -> {
                    if (element.isShorthand) {
                        element.addAfter(factory.createColon(), element.referenceNameElement)
                    }
                    if (element.expr == null) {
                        element.addAfter(context.expr, element.colon)
                    }
                }
                is RsPath -> when (val parent = element.parent) {
                    is RsPathExpr -> parent.replaceWithAddingParentheses(context.expr, factory)
                    else -> Unit // Can't replace RsPath to RsExpr
                }
                else -> element.replaceWithAddingParentheses(context.expr, factory)
            }
        }
        if (mode is InlineValueMode.InlineAllAndRemoveOriginal) {
            context.delete()
        }
    }

    override fun getCommandName(): String = RsBundle.message("command.name.inline", context.type, context.name)

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
        return RsInlineUsageViewDescriptor(context.element, RsBundle.message("list.item.to.inline", context.type.capitalized()))
    }
}

/** Replaces [this] either with `expr` or `(expr)`, depending on context */
fun RsElement.replaceWithAddingParentheses(expr: RsElement, factory: RsPsiFactory): PsiElement {
    val parent = parent
    val needsParentheses = when {
        expr is RsBinaryExpr && (parent is RsBinaryExpr || parent.requiresSingleExpr) -> true
        expr.isBlockLikeExpr && parent.requiresSingleExpr -> true
        expr is RsStructLiteral && (parent is RsMatchExpr || parent is RsForExpr || parent is RsCondition || parent is RsLetExpr) -> true
        else -> false
    }
    val newExpr = if (needsParentheses) {
        factory.createExpression("(${expr.text})")
    } else {
        expr
    }
    return replace(newExpr)
}

private val PsiElement.isBlockLikeExpr: Boolean
    get() =
        this is RsRangeExpr || this is RsLambdaExpr ||
        this is RsMatchExpr || this is RsBlockExpr ||
        this is RsLoopExpr || this is RsWhileExpr

private val PsiElement.requiresSingleExpr: Boolean
    get() = this is RsDotExpr || this is RsTryExpr || this is RsUnaryExpr || this is RsCastExpr || this is RsCallExpr
