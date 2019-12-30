/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.usages.Usage
import com.intellij.usages.UsageTarget
import com.intellij.usages.rules.ImportFilteringRule
import com.intellij.usages.rules.PsiElementUsage
import org.rust.lang.core.psi.RsUseSpeck
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorStrict

class RsImportFilteringRule : ImportFilteringRule() {
    override fun isVisible(usage: Usage, targets: Array<out UsageTarget>): Boolean {
        val psi = (usage as? PsiElementUsage)?.element as? RsElement ?: return true
        val useSpeck = psi.ancestorStrict<RsUseSpeck>()
        return !(useSpeck != null && useSpeck.alias == null)
    }
}
