/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsMacroArgument
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.stubs.index.RsIncludeMacroIndex

/**
 *  [RsExpandedElement]s are those elements which exist in temporary,
 *  in-memory PSI-files and are injected into real PSI. Their real
 *  parent is this temp PSI-file, but they are seen by the rest of
 *  the plugin as the children of [getContext] element.
 */
interface RsExpandedElement : RsElement {
    override fun getContext(): PsiElement?

    companion object {
        fun getContextImpl(psi: RsExpandedElement): PsiElement? {
            psi.expandedFrom?.let { return it.context }
            psi.getUserData(RS_EXPANSION_CONTEXT)?.let { return it }
            val parent = psi.stubParent
            if (parent is RsFile) {
                RsIncludeMacroIndex.getIncludingMod(parent)?.let { return it }
            }
            return parent
        }
    }
}

fun RsExpandedElement.setContext(context: RsElement) {
    putUserData(RS_EXPANSION_CONTEXT, context)
}

/**
 * The [RsMacroCall] that directly expanded to this element or
 * null if this element is not directly produced by a macro
 */
val RsExpandedElement.expandedFrom: RsMacroCall?
    get() = project.macroExpansionManager.getExpandedFrom(this)

val RsExpandedElement.expandedFromRecursively: RsMacroCall?
    get() {
        var call: RsMacroCall = expandedFrom ?: return null
        while (true) {
            call = call.expandedFrom ?: break
        }

        return call
    }

val RsExpandedElement.expandedFromSequence: Sequence<RsMacroCall>
    get() = generateSequence(expandedFrom) { it.expandedFrom }

fun PsiElement.findMacroCallExpandedFrom(): RsMacroCall? {
    val found = findMacroCallExpandedFromNonRecursive()
    return found?.findMacroCallExpandedFrom() ?: found
}

fun PsiElement.findMacroCallExpandedFromNonRecursive(): RsMacroCall? {
    return ancestors
        .filterIsInstance<RsExpandedElement>()
        .mapNotNull { it.expandedFrom }
        .firstOrNull()
}

val PsiElement.isExpandedFromMacro: Boolean
    get() = findMacroCallExpandedFromNonRecursive() != null

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
    val mappedElement = findElementExpandedFromNonRecursive() ?: return null
    return mappedElement.findElementExpandedFromUnchecked() ?: mappedElement
}

private fun PsiElement.findElementExpandedFromNonRecursive(): PsiElement? {
    val call = findMacroCallExpandedFromNonRecursive() ?: return null
    val mappedOffset = mapOffsetFromExpansionToCallBody(call, startOffset) ?: return null
    return call.containingFile.findElementAt(mappedOffset)
        ?.takeIf { it.startOffset == mappedOffset }
}

private fun mapOffsetFromExpansionToCallBody(call: RsMacroCall, offset: Int): Int? {
    val expansion = call.expansion ?: return null
    val fileOffset = call.expansionContext.expansionFileStartOffset
    return expansion.ranges.mapOffsetFromExpansionToCallBody(offset - fileOffset)
        ?.fromBodyRelativeOffset(call)
}

/**
 * If [this] element is inside a **macro call** body and this macro is successfully expanded, returns
 * a leaf element inside the macro expansion that is expanded from [this] element. Returns a
 * list of elements because an element inside a macro call body can be placed in a macro expansion
 * multiple times. Returns null if [this] element is not inside a macro call body, or the macro
 * expansion failed.
 *
 * # Examples
 *
 * Returns an empty list if [this] element is not placed to an expansion:
 *
 * ```rust
 * macro_rules foo { (bar) => { fn baz(){} } }
 * foo!(bar); // This `bar` is matched with the `bar` in the pattern and is not placed to the expansion
 * ```
 *
 * Returns a single-element list if [this] element is placed into the expansion only once:
 *
 * ```rust
 * macro_rules foo { ($i:ident) => { fn $i(){} } }
 * foo!(bar); // This `bar` is placed to the expansion as a function name
 * ```
 *
 * Returns a list of multiple elements if [this] element is placed into the expansion multiple times:
 *
 * ```rust
 * macro_rules foo { ($i:ident) => { fn $i(){} struct $i{} } }
 * foo!(bar); // This `bar` is placed to the expansion as a function name AND as a struct name
 * ```
 */
fun PsiElement.findExpansionElements(): List<PsiElement>? {
    val mappedElements = findExpansionElementsNonRecursive() ?: return null
    return mappedElements.flatMap { mappedElement ->
        mappedElement.findExpansionElements() ?: listOf(mappedElement)
    }
}

private fun PsiElement.findExpansionElementsNonRecursive(): List<PsiElement>? {
    val call = ancestorStrict<RsMacroArgument>()?.ancestorStrict<RsMacroCall>() ?: return null
    val expansion = call.expansion ?: return null
    val mappedOffsets = mapOffsetFromCallBodyToExpansion(call, expansion, startOffset) ?: return null
    val expansionFile = expansion.file
    return mappedOffsets.mapNotNull { mappedOffset ->
        expansionFile.findElementAt(mappedOffset)
            ?.takeIf { it.startOffset == mappedOffset }
    }
}

private fun mapOffsetFromCallBodyToExpansion(
    call: RsMacroCall,
    expansion: MacroExpansion,
    absOffsetInCallBody: Int
): List<Int>? {
    val relOffsetInCallBody = absOffsetInCallBody.toBodyRelativeOffset(call) ?: return null
    val fileOffset = call.expansionContext.expansionFileStartOffset
    return expansion.ranges.mapOffsetFromCallBodyToExpansion(relOffsetInCallBody)
        .map { it + fileOffset }
}

private fun Int.toBodyRelativeOffset(call: RsMacroCall): Int? {
    val macroOffset = call.bodyTextRange?.startOffset ?: return null
    val elementOffset = this - macroOffset
    check(elementOffset >= 0)
    return elementOffset
}

private fun Int.fromBodyRelativeOffset(call: RsMacroCall): Int? {
    val macroRange = call.bodyTextRange ?: return null
    val elementOffset = this + macroRange.startOffset
    check(elementOffset <= macroRange.endOffset)
    return elementOffset
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

private val RS_EXPANSION_CONTEXT = Key.create<RsElement>("org.rust.lang.core.psi.CODE_FRAGMENT_FILE")

