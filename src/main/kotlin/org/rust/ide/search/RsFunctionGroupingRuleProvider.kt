/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.openapi.project.Project
import com.intellij.usages.PsiNamedElementUsageGroupBase
import com.intellij.usages.Usage
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.FileStructureGroupRuleProvider
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.SingleParentUsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRule
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.ext.ancestorOrSelf

class RsFunctionGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule? {
        return RsFunctionGroupingRule()
    }

    private class RsFunctionGroupingRule : SingleParentUsageGroupingRule() {
        override fun getParentGroupFor(usage: Usage, targets: Array<UsageTarget>): UsageGroup? {
            if (usage !is PsiElementUsage) return null
            val rsFunction = usage.element.ancestorOrSelf<RsFunction>(RsImplItem::class.java) ?: return null
            return PsiNamedElementUsageGroupBase(rsFunction)
        }
    }
}
