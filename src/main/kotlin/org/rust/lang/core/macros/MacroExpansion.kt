/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileWithId
import org.rust.lang.core.macros.errors.MacroExpansionAndParsingError
import org.rust.lang.core.macros.errors.MacroExpansionAndParsingError.*
import org.rust.lang.core.macros.errors.MacroExpansionError
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.stdext.RsResult
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok
import org.rust.stdext.unwrapOrElse

enum class MacroExpansionContext {
    EXPR, PAT, TYPE, STMT, ITEM
}

val RsMacroCall.expansionContext: MacroExpansionContext
    get() = when (context) {
        is RsMacroExpr -> MacroExpansionContext.EXPR
        is RsBlock -> MacroExpansionContext.STMT
        is RsPatMacro -> MacroExpansionContext.PAT
        is RsMacroType -> MacroExpansionContext.TYPE
        else -> MacroExpansionContext.ITEM
    }

val RsPossibleMacroCall.expansionContext: MacroExpansionContext
    get() = when (val kind = kind) {
        is RsPossibleMacroCallKind.MacroCall -> kind.call.expansionContext
        is RsPossibleMacroCallKind.MetaItem -> MacroExpansionContext.ITEM
    }

val RsMacroCall.isExprOrStmtContext: Boolean
    get() {
        val expansionContext = expansionContext
        return expansionContext == MacroExpansionContext.EXPR || expansionContext == MacroExpansionContext.STMT
    }

sealed class MacroExpansion(val file: RsFile) {

    /** The list of expanded elements. Can be empty */
    abstract val elements: List<RsExpandedElement>

    class Expr(file: RsFile, val expr: RsExpr) : MacroExpansion(file) {
        override val elements: List<RsExpandedElement>
            get() = listOf(expr)
    }

    class Pat(file: RsFile, val pat: RsPat) : MacroExpansion(file) {
        override val elements: List<RsExpandedElement>
            get() = listOf(pat)
    }

    class Type(file: RsFile, val type: RsTypeReference) : MacroExpansion(file) {
        override val elements: List<RsExpandedElement>
            get() = listOf(type)
    }

    /** Can contains items, macros and macro calls */
    class Items(file: RsFile, override val elements: List<RsExpandedElement>) : MacroExpansion(file)

    /** Can contains items, statements and a tail expr */
    class Stmts(file: RsFile, override val elements: List<RsExpandedElement>) : MacroExpansion(file)
}

fun parseExpandedTextWithContext(
    context: MacroExpansionContext,
    factory: RsPsiFactory,
    expandedText: CharSequence
): MacroExpansion? {
    val file = factory.createPsiFile(context.prepareExpandedTextForParsing(expandedText))
        as? RsFile ?: return null
    return getExpansionFromExpandedFile(context, file)
}

/** Keep in sync with [MacroExpansionContext.expansionFileStartOffset] */
fun MacroExpansionContext.prepareExpandedTextForParsing(
    expandedText: CharSequence
): CharSequence = when (this) {
    MacroExpansionContext.EXPR -> "const C:T=$expandedText;"
    MacroExpansionContext.PAT -> "fn f($expandedText:())"
    MacroExpansionContext.TYPE -> "type T=$expandedText;"
    MacroExpansionContext.STMT -> "fn f(){$expandedText}"
    MacroExpansionContext.ITEM -> expandedText
}

val MacroExpansionContext.expansionFileStartOffset: Int
    get() = when (this) {
        MacroExpansionContext.EXPR -> 10
        MacroExpansionContext.PAT -> 5
        MacroExpansionContext.TYPE -> 7
        MacroExpansionContext.STMT -> 7
        MacroExpansionContext.ITEM -> 0
    }

/** If a call is previously expanded to [expandedFile], this function extract expanded elements from the file */
fun getExpansionFromExpandedFile(context: MacroExpansionContext, expandedFile: RsFile): MacroExpansion? {
    return when (context) {
        MacroExpansionContext.EXPR -> {
            val expr = expandedFile.stubDescendantOfTypeOrStrict<RsExpr>() ?: return null
            MacroExpansion.Expr(expandedFile, expr)
        }
        MacroExpansionContext.PAT -> {
            val pat = expandedFile.stubDescendantOfTypeOrStrict<RsPat>() ?: return null
            MacroExpansion.Pat(expandedFile, pat)
        }
        MacroExpansionContext.TYPE -> {
            val type = expandedFile.stubDescendantOfTypeOrStrict<RsTypeReference>() ?: return null
            MacroExpansion.Type(expandedFile, type)
        }
        MacroExpansionContext.STMT -> {
            val block = expandedFile.stubDescendantOfTypeOrStrict<RsBlock>() ?: return null
            val itemsAndStatements = block.stubChildrenOfType<RsExpandedElement>()
            MacroExpansion.Stmts(expandedFile, itemsAndStatements)
        }
        MacroExpansionContext.ITEM -> {
            val items = expandedFile.stubChildrenOfType<RsExpandedElement>()
            MacroExpansion.Items(expandedFile, items)
        }
    }
}

fun <T : RsMacroData, E : MacroExpansionError> MacroExpander<T, E>.expandMacro(
    def: RsMacroDataWithHash<T>,
    call: RsPossibleMacroCall,
    factory: RsPsiFactory,
    storeRangeMap: Boolean,
    useCache: Boolean,
): RsResult<MacroExpansion, MacroExpansionAndParsingError<E>> {
    val callData = RsMacroCallData.fromPsi(call) ?: return Err(MacroCallSyntaxError)
    val (expandedText, ranges) = if (useCache) {
        val callDataWithHash = RsMacroCallDataWithHash(callData, call.bodyHash)
        val mixHash = def.mixHash(callDataWithHash) ?: return Err(MacroCallSyntaxError)
        MacroExpansionSharedCache.getInstance().cachedExpand(this, def.data, callData, mixHash)
            .map { it.text to it.ranges }
    } else {
        expandMacroAsTextWithErr(def.data, callData)
    }.unwrapOrElse { return Err(ExpansionError(it)) }

    val context = call.expansionContext
    val expansion = parseExpandedTextWithContext(context, factory, expandedText)
        ?: return Err(ParsingError(expandedText, context))
    if (storeRangeMap) {
        expansion.file.putUserData(MACRO_RANGE_MAP_KEY, ranges)
    }
    return Ok(expansion)
}

private val MACRO_RANGE_MAP_KEY: Key<RangeMap> = Key.create("MACRO_RANGE_MAP_KEY")

val MacroExpansion.ranges: RangeMap
    get() {
        val file = file
        val virtualFile = file.virtualFile
        return if (virtualFile is VirtualFileWithId) {
            virtualFile.loadRangeMap()
        } else {
            // NEVER_CHANGED b/c light vfile will be fully replaced along with all caches after the macro change
            file.getUserData(MACRO_RANGE_MAP_KEY)
        } ?: RangeMap.EMPTY
    }
