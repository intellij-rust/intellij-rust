/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.openapi.project.Project
import com.intellij.usages.*
import com.intellij.usages.impl.FileStructureGroupRuleProvider
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.SingleParentUsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRule
import org.rust.ide.presentation.getStubOnlyText
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsTraitOrImpl
import org.rust.lang.core.psi.ext.ancestorOrSelf

class RsTraitOrImplGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule = RsImplGroupingRule()

    private class RsImplGroupingRule : SingleParentUsageGroupingRule() {
        override fun getParentGroupFor(usage: Usage, targets: Array<UsageTarget>): UsageGroup? {
            if (usage !is PsiElementUsage) return null
            val rsTraitOrImpl = usage.element.ancestorOrSelf<RsTraitOrImpl>() ?: return null
            return object : PsiElementUsageGroupBase<RsTraitOrImpl>(rsTraitOrImpl) {
                override fun getText(view: UsageView?): String = rsTraitOrImpl.presentableName ?: super.getText(view)
                override fun getPresentableName(): String = rsTraitOrImpl.presentableName ?: super.getPresentableName()
            }
        }

        val RsElement.presentableName: String?
            get() {
                return when (this) {
                    is RsNamedElement -> name
                    is RsImplItem -> {
                        val type = typeReference?.getStubOnlyText(renderLifetimes = false) ?: return null
                        val trait = traitRef?.getStubOnlyText(renderLifetimes = false)
                        buildString {
                            if (trait != null) {
                                append("$trait for ")
                            }
                            append(type)
                        }
                    }
                    else -> null
                }
            }
    }
}
