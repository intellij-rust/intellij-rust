/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2.util

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.stubs.StubElement
import com.intellij.util.SmartList
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.macros.*
import org.rust.lang.core.macros.decl.MACRO_DOLLAR_CRATE_IDENTIFIER
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.ext.basePath
import org.rust.lang.core.psi.ext.bodyTextRange
import org.rust.lang.core.resolve2.DeclMacroDefInfo
import org.rust.lang.core.resolve2.MacroCallInfo
import org.rust.lang.core.resolve2.RESOLVE_LOG
import org.rust.lang.core.stubs.*
import org.rust.openapiext.testAssert

/**
 * For each expanded [RsMacroCall] we store [RangeMap] - see [MacroCallInfo.dollarCrateMap]
 */
val RESOLVE_RANGE_MAP_KEY: Key<RangeMap> = Key("RESOLVE_RANGE_MAP_KEY")

/**
 * For [RsUseItem] and [RsMacroCall] we store `crateId`,
 * if `path` (path to macro or use path) starts with [MACRO_DOLLAR_CRATE_IDENTIFIER]
 */
val RESOLVE_DOLLAR_CRATE_ID_KEY: Key<CratePersistentId> = Key("RESOLVE_DOLLAR_CRATE_ID_KEY")

/**
 * If [RsMacroCall] is expanded from macro with ```#[macro_export(local_inner_macros)]``` attribute,
 * then we store `crateId` of macro definition
 */
val RESOLVE_LOCAL_INNER_MACROS_CRATE_ID_KEY: Key<CratePersistentId> = Key("RESOLVE_LOCAL_INNER_MACROS_CRATE_ID_KEY")

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
fun processDollarCrate(
    call: MacroCallInfo,
    def: DeclMacroDefInfo,
    file: RsFileStub,
    expansion: ExpansionResult
) {
    val rangesInFile = findCrateIdForEachDollarCrate(expansion, call, def)
    if (rangesInFile.isEmpty() && !def.hasLocalInnerMacros) return

    val ranges = expansion.ranges  // between `call.body` and `expandedText`
    // TODO: Possible optimization - [mapOffsetFromExpansionToCallBody] works in O(N), probably it can be optimized to O(log N)
    file.forEachTopLevelElement { element ->
        if (rangesInFile.isNotEmpty()) {
            processDollarCrateInsideExpandedElement(element, rangesInFile)
        }

        if (def.hasLocalInnerMacros && element is RsMacroCallStub) {
            val pathOffsetInExpansion = element.path.startOffset
            val pathOffsetInCall = ranges.mapOffsetFromExpansionToCallBody(pathOffsetInExpansion)
            val isExpandedFromDef = pathOffsetInCall == null
            if (isExpandedFromDef) {
                element.putUserData(RESOLVE_LOCAL_INNER_MACROS_CRATE_ID_KEY, def.crate)
            }
        }
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
                    val fragmentInCallBody = call.body
                        .subSequence(indexInCallBody, indexInCallBody + MACRO_DOLLAR_CRATE_IDENTIFIER.length)
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

/**
 * We are interested in these elements:
 * - [RsUseItem] - if path starts with [MACRO_DOLLAR_CRATE_IDENTIFIER]
 * - [RsMacroCall] - if path starts with [MACRO_DOLLAR_CRATE_IDENTIFIER] or if body contains [MACRO_DOLLAR_CRATE_IDENTIFIER]
 * - [RsMacro] - if body contains [MACRO_DOLLAR_CRATE_IDENTIFIER]
 */
private fun processDollarCrateInsideExpandedElement(
    element: StubElement<*>,
    rangesInFile: Map<Int, CratePersistentId>
) {
    fun filterRangesInside(range: TextRange): Map<Int, CratePersistentId> =
        rangesInFile.filterKeys { indexInFile ->
            val rangeInFile = TextRange(indexInFile, indexInFile + MACRO_DOLLAR_CRATE_IDENTIFIER.length)
            range.contains(rangeInFile)
        }

    when (element) {
        is RsUseItemStub -> {
            // expandedText = 'use $crate::foo;'
            val useSpeck = element.useSpeck ?: return
            useSpeck.forEachTopLevelPath {
                val crateId = rangesInFile[it.startOffset] ?: return@forEachTopLevelPath
                it.basePath().putUserData(RESOLVE_DOLLAR_CRATE_ID_KEY, crateId)
            }
        }
        is RsMacroCallStub -> {
            // expandedText = 'foo! { ... $crate ... }'
            run {
                val macroRangeInFile = element.bodyTextRange ?: return@run
                val rangesInMacro = filterRangesInside(macroRangeInFile)
                    .map { (indexInFile, crateId) ->
                        val indexInMacro = indexInFile - macroRangeInFile.startOffset
                        MappedTextRange(crateId, indexInMacro, MACRO_DOLLAR_CRATE_IDENTIFIER.length)
                    }
                if (rangesInMacro.isEmpty()) return@run
                element.putUserData(RESOLVE_RANGE_MAP_KEY, RangeMap.from(SmartList(rangesInMacro)))
            }

            // expandedText = '$crate::foo! { ... }'
            run {
                val path = element.path
                val crateId = rangesInFile[path.startOffset] ?: return@run
                path.basePath().putUserData(RESOLVE_DOLLAR_CRATE_ID_KEY, crateId)
            }
        }
    }
}

private fun StubElement<*>.forEachTopLevelElement(action: (StubElement<*>) -> Unit) {
    for (childStub in childrenStubs) {
        action(childStub)
        if (childStub is RsModItemStub || childStub is RsForeignModStub) {
            childStub.forEachTopLevelElement(action)
        }
    }
}

private fun RsUseSpeckStub.forEachTopLevelPath(consumer: (RsPathStub) -> Unit) {
    val path = path
    if (path != null) {
        consumer(path)
    } else {
        val useGroup = useGroup ?: return
        for (speck in useGroup.childrenStubs) {
            (speck as RsUseSpeckStub).forEachTopLevelPath(consumer)
        }
    }
}
