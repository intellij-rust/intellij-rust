/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2.util

import com.intellij.util.SmartList
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.macros.ExpansionResult
import org.rust.lang.core.macros.MacroCallBody
import org.rust.lang.core.macros.MappedTextRange
import org.rust.lang.core.macros.RangeMap
import org.rust.lang.core.macros.decl.MACRO_DOLLAR_CRATE_IDENTIFIER
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.resolve2.DeclMacroDefInfo
import org.rust.lang.core.resolve2.MacroCallInfo
import org.rust.lang.core.resolve2.RESOLVE_LOG
import org.rust.openapiext.testAssert

/**
 * Algorithm: for each [DeclMacroDefInfo] and [MacroCallInfo] maintain map
 * from index of [MACRO_DOLLAR_CRATE_IDENTIFIER] occurrence in text to corresponding [CratePersistentId].
 * When expand macro call, we want for each occurrence
 * of [MACRO_DOLLAR_CRATE_IDENTIFIER] in `expansion.text` to find corresponding [CratePersistentId].
 * [MACRO_DOLLAR_CRATE_IDENTIFIER] could come from:
 * - '$crate' from macro itself (macro_rules) - use [DeclMacroDefInfo.crate]
 * - [MACRO_DOLLAR_CRATE_IDENTIFIER] from macro itself (macro_rules) - use map from [DeclMacroDefInfo]
 * - [MACRO_DOLLAR_CRATE_IDENTIFIER] from macro call - use map from [MacroCallInfo]
 */
fun createDollarCrateHelper(
    call: MacroCallInfo,
    def: DeclMacroDefInfo,
    expansion: ExpansionResult
): DollarCrateHelper? {
    val rangesInFile = findCrateIdForEachDollarCrate(expansion, call, def)
    if (rangesInFile.isEmpty() && !def.hasLocalInnerMacros) return null
    return DollarCrateHelper(expansion.ranges, rangesInFile, def.hasLocalInnerMacros, def.crate)
}

/**
 * We are interested in these elements:
 * - [RsUseItem] - if path starts with [MACRO_DOLLAR_CRATE_IDENTIFIER]
 * - [RsMacroCall] - if path starts with [MACRO_DOLLAR_CRATE_IDENTIFIER] or if body contains [MACRO_DOLLAR_CRATE_IDENTIFIER]
 * - [RsMacro] - if body contains [MACRO_DOLLAR_CRATE_IDENTIFIER]
 */
class DollarCrateHelper(
    // between `call.body` and `expandedText`
    private val ranges: RangeMap,
    // between `expandedText` and crate ids
    private val rangesInExpansion: Map<Int, CratePersistentId>,
    private val defHasLocalInnerMacros: Boolean,
    private val defCrate: CratePersistentId,
) {

    /**
     * - expandedText = 'use $crate::foo;'
     * - expandedText = '$crate::foo! { ... }'
     * - expandedText = 'foo!()'  (we should "add" '$crate')
     */
    fun convertPath(path: Array<String>, offsetInExpansion: Int): Array<String> {
        return when {
            path[0] == MACRO_DOLLAR_CRATE_IDENTIFIER -> {
                val crateId = rangesInExpansion[offsetInExpansion]
                if (crateId != null) {
                    arrayOf(MACRO_DOLLAR_CRATE_IDENTIFIER, crateId.toString()) + path.copyOfRange(1, path.size)
                } else {
                    RESOLVE_LOG.error("Can't find crate for path starting with \$crate")
                    path
                }
            }
            defHasLocalInnerMacros -> {
                val pathOffsetInCall = ranges.mapOffsetFromExpansionToCallBody(offsetInExpansion)
                val isExpandedFromDef = pathOffsetInCall == null
                if (!isExpandedFromDef) return path
                arrayOf(MACRO_DOLLAR_CRATE_IDENTIFIER, defCrate.toString()) + path
            }
            else -> path
        }
    }

    /** expandedText = 'foo! { ... $crate ... }' */
    fun getRangeMap(startOffsetInExpansion: Int, endOffsetInExpansion: Int): RangeMap {
        val rangesInMacro = filterRangesInside(startOffsetInExpansion, endOffsetInExpansion)
            .map { (offsetInExpansion, crateId) ->
                val offsetInMacro = offsetInExpansion - startOffsetInExpansion
                MappedTextRange(crateId, offsetInMacro, MACRO_DOLLAR_CRATE_IDENTIFIER.length)
            }
        if (rangesInMacro.isEmpty()) return RangeMap.EMPTY
        return RangeMap.from(SmartList(rangesInMacro))
    }

    private fun filterRangesInside(macroStart: Int, macroEnd: Int): Map<Int, CratePersistentId> =
        rangesInExpansion.filterKeys { rangeStart ->
            val rangeEnd = rangeStart + MACRO_DOLLAR_CRATE_IDENTIFIER.length
            macroStart <= rangeStart && rangeEnd <= macroEnd
        }
}

/**
 * Entry `(index, crateId)` in returning map means that
 * `expansion.text` starting from `index` contains [MACRO_DOLLAR_CRATE_IDENTIFIER] which corresponds to `crateId`
 */
private fun findCrateIdForEachDollarCrate(
    expansion: ExpansionResult,
    call: MacroCallInfo,
    def: DeclMacroDefInfo
): Map<Int, CratePersistentId> {
    val ranges = expansion.ranges  // between `call.body` and `expandedText`
    return expansion.dollarCrateOccurrences.asSequence()
        .mapNotNull { indexInExpandedText ->
            val indexInCallBody = ranges.mapOffsetFromExpansionToCallBody(indexInExpandedText)
            val crateId: CratePersistentId = if (indexInCallBody != null) {
                testAssert {
                    val fragmentInCallBody = (call.body as? MacroCallBody.FunctionLike)?.text
                        ?.subSequence(indexInCallBody, indexInCallBody + MACRO_DOLLAR_CRATE_IDENTIFIER.length)
                    fragmentInCallBody == MACRO_DOLLAR_CRATE_IDENTIFIER
                }
                call.dollarCrateMap.mapOffsetFromExpansionToCallBody(indexInCallBody)
                    ?: run {
                        RESOLVE_LOG.error("Unexpected macro expansion. Macro call: '$call', expansion: '${expansion.text}'")
                        return@mapNotNull null
                    }
            } else {
                // TODO: We should use [RangeMap] between `expansion.text` and macro body (macro_rules),
                //  because there can be [MACRO_DOLLAR_CRATE_IDENTIFIER] in macro body (and not only '$crate')
                def.crate
            }
            indexInExpandedText to crateId
        }
        .toMap(hashMapOf())
}
