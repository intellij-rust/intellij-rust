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
import org.rust.lang.core.psi.ext.RsTraitOrImpl
import org.rust.lang.core.psi.ext.ancestorOrSelf

class RsTraitOrImplGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule = RsImplGroupingRule()

    private class RsImplGroupingRule : SingleParentUsageGroupingRule() {
        override fun getParentGroupFor(usage: Usage, targets: Array<UsageTarget>): UsageGroup? {
            if (usage !is PsiElementUsage) return null
            val traitOrImpl = usage.element.ancestorOrSelf<RsTraitOrImpl>() ?: return null
            return RsImplUsageGroup(traitOrImpl)
        }

        private class RsImplUsageGroup(
            traitOrImpl: RsTraitOrImpl
        ) : PsiElementUsageGroupBase<RsTraitOrImpl>(traitOrImpl) {
            private val name: String? = run {
                when (traitOrImpl) {
                    is RsImplItem -> {
                        val type = traitOrImpl.typeReference?.getStubOnlyText(renderLifetimes = false)
                            ?: return@run null
                        val trait = traitOrImpl.traitRef?.getStubOnlyText(renderLifetimes = false)
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

            override fun getText(view: UsageView?): String = name ?: super.getText(view)
            override fun getPresentableName(): String = name ?: super.getPresentableName()
        }
    }
}
