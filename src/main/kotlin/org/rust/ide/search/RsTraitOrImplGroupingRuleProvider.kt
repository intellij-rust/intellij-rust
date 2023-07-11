/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.openapi.project.Project
import com.intellij.usages.PsiElementUsageGroupBase
import com.intellij.usages.Usage
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.FileStructureGroupRuleProvider
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.SingleParentUsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRule
import org.jetbrains.annotations.Nls
import org.rust.RsBundle
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
            @Nls
            private val name: String? = run {
                when (traitOrImpl) {
                    is RsImplItem -> {
                        val type = traitOrImpl.typeReference?.getStubOnlyText(renderLifetimes = false)
                            ?: return@run null
                        val trait = traitOrImpl.traitRef?.getStubOnlyText(renderLifetimes = false)
                        if (trait != null) RsBundle.message("0.for.1", trait,type) else type
                    }
                    else -> null
                }
            }

            override fun getPresentableGroupText(): String = name ?: super.getPresentableGroupText()
            override fun getPresentableName(): String = name ?: super.getPresentableName()
        }
    }
}
