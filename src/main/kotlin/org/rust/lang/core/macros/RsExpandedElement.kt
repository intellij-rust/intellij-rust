/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

/**
 *  [RsExpandedElement]s are those elements which exist in temporary,
 *  in-memory PSI-files and are injected into real PSI. Their real
 *  parent is this temp PSI-file, but they are seen by the rest of
 *  the plugin as the children of [getContext] element.
 */
interface RsExpandedElement : RsElement {
    override fun getContext(): PsiElement?

    companion object {
        fun getContextImpl(psi: RsExpandedElement, isIndexAccessForbidden: Boolean = false): PsiElement? {
            psi.getUserData(RS_EXPANSION_CONTEXT)?.let { return it }
            val parent = psi.parent
            if (parent is RsFile && !isIndexAccessForbidden) {
                val project = parent.project
                if (!DumbService.isDumb(project)) {
                    val macroExpansionManager = project.macroExpansionManager
                    macroExpansionManager.getContextOfMacroCallExpandedFrom(parent)?.let { return it }
                    if (parent.isIncludedByIncludeMacro) {
                        macroExpansionManager.getIncludedFrom(parent)?.let { return it.containingMod }
                    }
                }
            }
            return parent
        }
    }
}

fun RsExpandedElement.setContext(context: RsElement) {
    (containingFile as? RsFile)?.setRsFileContext(context, isInMemoryMacroExpansion = false)
    setExpandedElementContext(context)
}

/** Internal. Use [setContext] */
fun RsExpandedElement.setExpandedElementContext(context: RsElement) {
    putUserData(RS_EXPANSION_CONTEXT, context)
}

/** Internal. Use [setContext] */
fun RsFile.setRsFileContext(context: RsElement, isInMemoryMacroExpansion: Boolean) {
    val contextContainingFile = context.containingRsFileSkippingCodeFragments
    if (contextContainingFile != null) {
        inheritCachedDataFrom(contextContainingFile, isInMemoryMacroExpansion)
    }
}

fun RsFile.setForcedReducedRangeMap(ranges: RangeMap) {
    putUserData(RS_FORCED_REDUCED_RANGE_MAP, ranges)
}

/**
 * The [RsMacroCall] that directly expanded to this element or
 * null if this element is not directly produced by a macro.
 * Doesn't support `include!` macro - see [RsExpandedElement.expandedOrIncludedFrom]
 */
val RsExpandedElement.expandedFrom: RsPossibleMacroCall?
    get() = project.macroExpansionManager.getExpandedFrom(this)

val RsExpandedElement.expandedFromRecursively: RsPossibleMacroCall?
    get() {
        var call: RsPossibleMacroCall = expandedFrom ?: return null
        while (true) {
            call = call.expandedFrom ?: break
        }

        return call
    }

val RsExpandedElement.expandedFromSequence: Sequence<RsMacroCall>
    get() = generateSequence(expandedFrom as? RsMacroCall) { it.expandedFrom as? RsMacroCall }

val PsiElement.includedFrom: RsMacroCall?
    get() {
        val containingFile = stubParent as? RsFile ?: return null
        return if (containingFile.isIncludedByIncludeMacro) {
            containingFile.project.macroExpansionManager.getIncludedFrom(containingFile)
        } else {
            null
        }
    }

val RsExpandedElement.expandedOrIncludedFrom: RsPossibleMacroCall?
    get() = expandedFrom ?: includedFrom

fun PsiElement.findMacroCallExpandedFrom(): RsPossibleMacroCall? {
    val found = findMacroCallExpandedFromNonRecursive()
    return found?.findMacroCallExpandedFrom() ?: found
}

fun PsiElement.findMacroCallExpandedFromNonRecursive(): RsPossibleMacroCall? {
    return stubAncestors
        .filterIsInstance<RsExpandedElement>()
        .mapNotNull { it.expandedFrom }
        .firstOrNull()
}

val PsiElement.isExpandedFromMacro: Boolean
    get() = findMacroCallExpandedFromNonRecursive() != null

val PsiElement.isExpandedFromIncludeMacro: Boolean
    get() {
        val containingFile = stubParent as? RsFile ?: return false
        return containingFile.isIncludedByIncludeMacro
    }

val PsiElement.macroCallExpandedFromSequence: Sequence<RsPossibleMacroCall>
    get() {
        val first = findMacroCallExpandedFromNonRecursive() ?: return emptySequence()
        return generateSequence(first) { it.findMacroCallExpandedFromNonRecursive() }
    }

data class MacroCallAndOffset(val call: RsPossibleMacroCall, val absoluteOffset: Int)

/**
 * If [this] is inside a **macro expansion**, returns a leaf element inside a macro call from which
 * the first token of this element is expanded. Returns null if [this] element is not inside a
 * macro expansion or source element is not a part of a macro call (i.e. is a part of a macro
 * definition)
 *
 * If [strict] is `true`, always returns an element inside a root macro call, i.e. outside of any
 * expansion, or null otherwise.
 *
 * # Examples
 *
 * ```rust
 * macro_rules! foo {
 *     ($i:ident) => { struct $i; }
 * }
 * // Source code    // Expansion
 * foo!(Bar);        // struct Bar;
 * //     \____________________/
 *                           //^ For this element returns `Bar` element in the macro call.
 *                           // It works the same regardless the [strict] value
 * ```
 *
 * ```rust
 * macro_rules! foo {
 *     ($i:item) => { $i }
 * }
 * macro_rules! bar {
 *     ($i:ident) => { struct $i; }
 * }
 * // Source code       // Expansion step 1    // Expansion step 2
 * foo! { bar!(Baz); }  // bar!(Baz);          // struct Baz;
 * //            \_______________________________________/
 *                                                     //^ For this element returns `Baz` element in the macro call.
 *                                                     // It works the same regardless the [strict] value
 * ```
 *
 * ```rust
 * macro_rules! foo {
 *     () => { bar!(Baz); }
 * }
 * macro_rules! bar {
 *     ($i:ident) => { struct $i; }
 * }
 * // Source code  // Expansion step 1    // Expansion step 2
 * foo!();         // bar!(Baz);          // struct Baz;
 * //                        \______________________/
 *                                                //^ For this element returns `Baz` element in the intermediate
 *                                                // macro call ONLY if the [strict] value is `false`.
 *                                                // Returns null otherwise
 * ```
 */
fun PsiElement.findElementExpandedFrom(strict: Boolean = true): PsiElement? {
    val expandedFrom = findElementExpandedFromUnchecked()
    return if (strict) expandedFrom?.takeIf { !it.isExpandedFromMacro } else expandedFrom
}

private fun PsiElement.findElementExpandedFromUnchecked(): PsiElement? {
    val (anchor, offset) = findMacroCallAndOffsetExpandedFromUnchecked(this, startOffset, RangeMap.StickTo.RIGHT)
        ?: return null
    return anchor.containingFile.findElementAt(offset)
        ?.takeIf { it.startOffset == offset }
}

fun findMacroCallAndOffsetExpandedFromUnchecked(
    anchor: PsiElement,
    startOffset: Int,
    stickTo: RangeMap.StickTo,
): MacroCallAndOffset? {
    val ranges = anchor.containingFile.getUserData(RS_FORCED_REDUCED_RANGE_MAP)
    if (ranges != null) {
        val mappedOffset = ranges.mapOffsetFromExpansionToCallBody(startOffset, stickTo) ?: return null
        val call = anchor.findMacroCallExpandedFrom() ?: return null
        return MacroCallAndOffset(call, mappedOffset.fromBodyRelativeOffset(call) ?: return null)
    }
    val mappedElement = findMacroCallAndOffsetExpandedFromNonRecursive(anchor, startOffset, stickTo) ?: return null
    return findMacroCallAndOffsetExpandedFromUnchecked(mappedElement.call, mappedElement.absoluteOffset, stickTo) ?: mappedElement
}

private fun findMacroCallAndOffsetExpandedFromNonRecursive(
    anchor: PsiElement,
    startOffset: Int,
    stickTo: RangeMap.StickTo,
): MacroCallAndOffset? {
    val call = anchor.findMacroCallExpandedFromNonRecursive() ?: return null
    val mappedOffset = mapOffsetFromExpansionToCallBody(call, startOffset, stickTo) ?: return null
    return MacroCallAndOffset(call, mappedOffset)
}

private fun mapOffsetFromExpansionToCallBody(
    call: RsPossibleMacroCall,
    offset: Int,
    stickTo: RangeMap.StickTo,
): Int? {
    return mapOffsetFromExpansionToCallBodyRelative(call, offset, stickTo)
        ?.fromBodyRelativeOffset(call)
}

private fun mapOffsetFromExpansionToCallBodyRelative(
    call: RsPossibleMacroCall,
    offset: Int,
    stickTo: RangeMap.StickTo,
): Int? {
    val expansion = call.expansion ?: return null
    val fileOffset = call.expansionContext.expansionFileStartOffset
    return expansion.ranges.mapOffsetFromExpansionToCallBody(offset - fileOffset, stickTo)
}

fun PsiElement.cameFromMacroCall(): Boolean {
    val call = findMacroCallExpandedFromNonRecursive() as? RsMacroCall ?: return false
    val startOffset = (this as? RsPath)?.greenStub?.startOffset ?: startOffset
    return mapOffsetFromExpansionToCallBodyRelative(call, startOffset, RangeMap.StickTo.RIGHT) != null
}

/**
 * Works like [findElementExpandedFrom]`(strict = false)`, but returns [RsMacroCall] instead of a leaf inside it.
 * Does not switch to AST if [this] is [RsPath]. Very specific to hygiene
 */
fun PsiElement.findMacroCallFromWhichLeafIsExpanded(): RsPossibleMacroCall? {
    val startOffset = (this as? RsPath)?.greenStub?.startOffset ?: startOffset
    return findMacroCallAndOffsetExpandedFromUnchecked(this, startOffset, RangeMap.StickTo.RIGHT)?.call
}

/**
 * If [this] element is inside a (function-like or attribute)
 * **macro call** body and this macro is successfully expanded, returns
 * a leaf element inside the macro expansion that is expanded from [this] element. Returns a
 * list of elements because an element inside a macro call body can be placed in a macro expansion
 * multiple times. Returns `null` if [this] element is not inside a macro call body.
 * Works for both declarative and procedural macros.
 *
 * # Examples
 *
 * Returns an *empty list* if the macro expansion failed or [this] element is not placed to an expansion:
 *
 * ```rust
 * macro_rules foo { (bar) => { fn baz(){} } }
 * foo!(bar); // This `bar` is matched with the `bar` in the pattern and is not placed to the expansion
 * ```
 *
 * Returns a *single-element list* if [this] element is placed into the expansion only once:
 *
 * ```rust
 * macro_rules foo { ($i:ident) => { fn $i(){} } }
 * foo!(bar); // This `bar` is placed to the expansion as a function name
 * ```
 *
 * Returns a *list of multiple elements* if [this] element is placed into the expansion multiple times:
 *
 * ```rust
 * macro_rules foo { ($i:ident) => { fn $i(){} struct $i{} } }
 * foo!(bar); // This `bar` is placed to the expansion as a function name AND as a struct name
 * ```
 *
 * Works with attribute procedural macros as well:
 *
 * ```rust
 * #[foo]
 * fn bar() {} // Maps each token with a corresponding token in the macro expansion
 * ```
 */
fun PsiElement.findExpansionElements(cache: AttrCache = AttrCache.NoCache): List<PsiElement>? {
    val mappedElements = findExpansionElementsNonRecursive(cache) ?: return null
    return mappedElements.flatMap { mappedElement ->
        mappedElement.findExpansionElements()?.takeIf { it.isNotEmpty() } ?: listOf(mappedElement)
    }
}

fun PsiElement.findExpansionElementOrSelf(): PsiElement =
    findExpansionElements()?.singleOrNull() ?: this

private fun PsiElement.findExpansionElementsNonRecursive(cache: AttrCache): List<PsiElement>? {
    val ancestors = ancestors.toList()
    val call = ancestors.asReversed().firstNotNullOfOrNull {
        when (it) {
            is RsMacroArgument -> it.ancestorStrict<RsMacroCall>()
            is RsAttrProcMacroOwner -> cache.cachedGetProcMacroAttribute(it)
            else -> null
        }
    } ?: return null

    if (call is RsMetaItem) {
        val path = call.path
        val isAttributeOnMacro = ancestors.any {
            it == path || it is RsMetaItem && it.isRootMetaItem() && it.name == "cfg"
        }
        if (isAttributeOnMacro) return null
    }

    val expansion = call.expansion ?: return emptyList()
    val mappedOffsets = mapOffsetFromCallBodyToExpansion(call, expansion, startOffset) ?: return emptyList()
    val expansionFile = expansion.file
    return mappedOffsets.mapNotNull { mappedOffset ->
        expansionFile.findElementAt(mappedOffset)
            ?.takeIf { it.startOffset == mappedOffset }
    }
}

private fun mapOffsetFromCallBodyToExpansion(
    call: RsPossibleMacroCall,
    expansion: MacroExpansion,
    absOffsetInCallBody: Int
): List<Int>? {
    val relOffsetInCallBody = absOffsetInCallBody.toBodyRelativeOffset(call) ?: return null
    val fileOffset = call.expansionContext.expansionFileStartOffset
    return expansion.ranges.mapOffsetFromCallBodyToExpansion(relOffsetInCallBody)
        .map { it + fileOffset }
}

private fun Int.toBodyRelativeOffset(call: RsPossibleMacroCall): Int? {
    val bodyTextRange = call.bodyTextRange ?: return null
    if (this !in bodyTextRange) return null
    val macroOffset = bodyTextRange.startOffset
    val elementOffset = this - macroOffset
    check(elementOffset >= 0)
    return elementOffset
}

private fun Int.fromBodyRelativeOffset(call: RsPossibleMacroCall): Int? {
    val macroRange = call.bodyTextRange ?: return null
    val elementOffset = this + macroRange.startOffset
    check(elementOffset <= macroRange.endOffset)
    return elementOffset
}

private fun MappedTextRange.fromBodyRelativeRange(call: RsPossibleMacroCall): MappedTextRange? {
    val newSrcOffset = srcOffset.fromBodyRelativeOffset(call) ?: return null
    return MappedTextRange(newSrcOffset, dstOffset, length)
}

fun RsPossibleMacroCall.mapRangeFromExpansionToCallBodyStrict(range: TextRange): TextRange? {
    return mapRangeFromExpansionToCallBody(range).singleOrNull()?.takeIf { it.length == range.length }
}

fun RsPossibleMacroCall.mapRangeFromExpansionToCallBody(range: TextRange): List<TextRange> {
    val expansion = expansion ?: return emptyList()
    return mapRangeFromExpansionToCallBody(expansion, this, range)
}

fun mapRangeFromExpansionToCallBody(
    expansion: MacroExpansion,
    call: RsPossibleMacroCall,
    range: TextRange
): List<TextRange> {
    return mapRangeFromExpansionToCallBody(
        expansion,
        call,
        MappedTextRange(range.startOffset, range.startOffset, range.length)
    ).map { it.srcRange }
}

private fun mapRangeFromExpansionToCallBody(
    expansion: MacroExpansion,
    call: RsPossibleMacroCall,
    range: MappedTextRange
): List<MappedTextRange> {
    val fileOffset = call.expansionContext.expansionFileStartOffset
    if (range.srcOffset - fileOffset < 0) return emptyList()
    val mappedRanges = expansion.ranges.mapMappedTextRangeFromExpansionToCallBody(range.srcShiftLeft(fileOffset))
        .mapNotNull { it.fromBodyRelativeRange(call) }
    val parentCall = call.findMacroCallExpandedFromNonRecursive() ?: return mappedRanges
    return mappedRanges.flatMap {
        val parentExpansion = parentCall.expansion ?: return emptyList() // impossible?
        mapRangeFromExpansionToCallBody(parentExpansion, parentCall, it)
    }
}

fun mapRangeFromExpansionToCallBodyStrict(anchor: PsiElement, range: TextRange): TextRange? {
    val ranges = anchor.containingFile.getUserData(RS_FORCED_REDUCED_RANGE_MAP)
    if (ranges != null) {
        return ranges.mapTextRangeFromExpansionToCallBody(range)
            .singleOrNull()
            ?.srcRange
            ?.takeIf { it.length == range.length }
    }
    val call = anchor.findMacroCallExpandedFromNonRecursive() ?: return null
    return call.mapRangeFromExpansionToCallBodyStrict(range)
}

/**
 * If receiver element is inside a macro expansion, returns the element inside the macro call
 * we should navigate to (or the macro call itself if there isn't such element inside a macro call).
 * Returns null if the element isn't inside a macro expansion
 */
fun PsiElement.findNavigationTargetIfMacroExpansion(): PsiElement? {
    /** @see RsNamedElementImpl.getTextOffset */
    val element = (this as? RsNameIdentifierOwner)?.nameIdentifier ?: this
    return element.findElementExpandedFrom() ?: findMacroCallExpandedFrom()?.path
}

private val RS_EXPANSION_CONTEXT = Key.create<RsElement>("org.rust.lang.core.psi.RS_EXPANSION_CONTEXT")
private val RS_FORCED_REDUCED_RANGE_MAP = Key.create<RangeMap>("org.rust.lang.core.psi.RS_FORCED_REDUCED_RANGE_MAP")
