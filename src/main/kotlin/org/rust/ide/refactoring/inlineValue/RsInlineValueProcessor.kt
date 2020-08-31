/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inlineValue

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.rust.ide.refactoring.RsInlineUsageViewDescriptor
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsStructLiteralField
import org.rust.lang.core.resolve.ref.RsReference

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
                    if (element.colon == null) {
                        element.addAfter(factory.createColon(), element.referenceNameElement)
                    }
                    if (element.expr == null) {
                        element.addAfter(context.expr, element.colon)
                    }
                }
                else -> element.replace(context.expr)
            }
        }
        if (mode is InlineValueMode.InlineAllAndRemoveOriginal) {
            context.delete()
        }
    }

    override fun getCommandName(): String = "Inline ${context.type} ${context.name}"

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
        return RsInlineUsageViewDescriptor(context.element, "${context.type.capitalize()} to inline")
    }
}
