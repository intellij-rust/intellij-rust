/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.ext.RsPossibleMacroCall
import org.rust.lang.core.psi.ext.existsAfterExpansion
import org.rust.lang.core.psi.ext.expansion

fun RsPossibleMacroCall.prepareForExpansionHighlighting(
    ancestorMacro: MacroCallPreparedForHighlighting? = null
): MacroCallPreparedForHighlighting? {
    if (this is RsMacroCall && macroArgument == null) return null // special macros should not be highlighted
    if (!existsAfterExpansion) return null
    val expansion = expansion ?: return null
    val isDeeplyAttrMacro = (ancestorMacro == null || ancestorMacro.isDeeplyAttrMacro) && this is RsMetaItem
    return MacroCallPreparedForHighlighting(this, expansion, isDeeplyAttrMacro)
}

data class MacroCallPreparedForHighlighting(
    val macroCall: RsPossibleMacroCall,
    val expansion: MacroExpansion,
    val isDeeplyAttrMacro: Boolean,
) {
    val elementsForHighlighting: List<PsiElement>
        get() {
            if (expansion.ranges.isEmpty()) return emptyList()
            // Don't try to restrict range by `getElementsInRange`: it does not return all ancestors
            // even if `includeAllParents = true`
            return CollectHighlightsUtil.getElementsInRange(expansion.file, 0, expansion.file.textLength)
        }
}
