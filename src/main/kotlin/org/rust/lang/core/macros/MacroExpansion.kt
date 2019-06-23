/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileWithId
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.stubChildrenOfType
import org.rust.lang.core.psi.ext.stubDescendantOfTypeOrStrict

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
): MacroExpansion? =
    getExpansionFromExpandedFile(context, factory.createFile(context.prepareExpandedTextForParsing(expandedText)))

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

fun MacroExpander.expandMacro(
    def: RsMacro,
    call: RsMacroCall,
    factory: RsPsiFactory,
    storeRangeMap: Boolean
): MacroExpansion? {
    val (expandedText, ranges) = expandMacroAsText(def, call) ?: return null
    return parseExpandedTextWithContext(call.expansionContext, factory, expandedText)?.also {
        if (storeRangeMap) it.file.putUserData(MACRO_RANGE_MAP_KEY, ranges)
    }
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
