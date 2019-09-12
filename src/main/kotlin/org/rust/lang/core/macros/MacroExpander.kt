/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.SmartList
import org.rust.lang.core.parser.RustParserUtil.collapsedTokenType
import org.rust.lang.core.parser.createRustPsiBuilder
import org.rust.lang.core.parser.rawLookupText
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.doc.psi.RsDocKind
import org.rust.openapiext.Testmark
import org.rust.openapiext.forEachChild
import org.rust.stdext.joinToWithBuffer
import org.rust.stdext.mapNotNullToSet
import java.util.*
import java.util.Collections.singletonMap

data class MacroSubstitution(
    /**
     * Maps meta variable names with the actual values, e.g. for this macro
     * ```rust
     * macro_rules! foo {
     *     ($ i:item) => ( $i )
     * }
     * foo!( mod a {} )
     * ```
     * we map "i" to "mod a {}"
     */
    val variables: Map<String, MetaVarValue>,

    /**
     * Contains macro groups values. E.g. for this macro
     * ```rust
     * macro_rules! foo {
     *     ($($ i:item),*; $($ e:expr),*) => {
     *         fn foo() { $($ e);*; }
     *         $($ i)*
     *     }
     * }
     * foo! { mod a {}, mod b {}; 1, 2 }
     * ```
     * It will contains
     * ```
     * [
     *     MacroGroup {
     *         substs: [
     *             {"i" => "mod a {}"},
     *             {"i" => "mod b {}"}
     *         ]
     *     },
     *     MacroGroup {
     *         substs: [
     *             {"e" => "1"},
     *             {"e" => "2"}
     *         ]
     *     }
     * ]
     * ```
     */
    val groups: List<MacroGroup>
)

data class MacroGroup(
    val definition: RsMacroBindingGroup,
    val substs: List<MacroSubstitution>
)

private data class WithParent(
    private val subst: MacroSubstitution,
    private val parent: WithParent?
) {
    val groups: List<MacroGroup>
        get() = subst.groups

    fun getVar(name: String): MetaVarValue? =
        subst.variables[name] ?: parent?.getVar(name)
}

data class MetaVarValue(
    val value: String,
    val kind: FragmentKind?,
    val offsetInCallBody: Int
)

class MacroExpander(val project: Project) {
    fun expandMacroAsText(def: RsMacro, call: RsMacroCall): Pair<CharSequence, RangeMap>? {
        val (case, subst, loweringRanges) = findMatchingPattern(def, call) ?: return null
        val macroExpansion = case.macroExpansion ?: return null

        val substWithGlobalVars = WithParent(
            subst,
            WithParent(
                MacroSubstitution(
                    singletonMap("crate", MetaVarValue(expandDollarCrateVar(call, def), null, -1)),
                    emptyList()
                ),
                null
            )
        )

        return substituteMacro(macroExpansion.macroExpansionContents, substWithGlobalVars)?.let { (text, ranges) ->
            text to loweringRanges.mapAll(ranges)
        }
    }

    private fun findMatchingPattern(
        def: RsMacro,
        call: RsMacroCall
    ): Triple<RsMacroCase, MacroSubstitution, RangeMap>? {
        val (macroCallBody, ranges) = project.createRustPsiBuilder(call.macroBody ?: return null).lowerDocComments()
        macroCallBody.eof() // skip whitespace
        var start = macroCallBody.mark()
        val macroCaseList = def.macroBodyStubbed?.macroCaseList ?: return null

        for (case in macroCaseList) {
            val subst = case.pattern.match(macroCallBody)
            if (subst != null) {
                return Triple(case, subst, ranges)
            } else {
                start.rollbackTo()
                start = macroCallBody.mark()
            }
        }

        return null
    }

    /** Rustc replaces doc comments like `/// foo` to attributes `#[doc = "foo"]` before macro expansion */
    private fun PsiBuilder.lowerDocComments(): Pair<PsiBuilder, RangeMap> {
        if (!hasDocComments()) {
            return this to if (originalText.isNotEmpty()) {
                RangeMap.from(SmartList(MappedTextRange(0, 0, originalText.length)))
            } else {
                RangeMap.EMPTY
            }
        }

        MacroExpansionMarks.docsLowering.hit()

        val sb = StringBuffer((originalText.length * 1.1).toInt())
        val ranges = SmartList<MappedTextRange>()

        var i = 0
        while (true) {
            val token = rawLookup(i) ?: break
            val text = rawLookupText(i)
            val start = rawTokenTypeStart(i)
            i++

            if (token in RS_DOC_COMMENTS) {
                // TODO calculate how many `#` we should insert
                sb.append("#[doc=r###\"")
                RsDocKind.of(token).removeDecoration(text.splitToSequence("\n")).joinTo(sb, separator = "\n")
                sb.append("\"###]")
            } else {
                ranges.mergeAdd(MappedTextRange(start, sb.length, text.length))
                sb.append(text)
            }
        }

        return this@MacroExpander.project.createRustPsiBuilder(sb) to RangeMap.from(ranges)
    }

    private fun PsiBuilder.hasDocComments(): Boolean {
        var i = 0
        while (true) {
            val token = rawLookup(i++) ?: break
            if (token in RS_DOC_COMMENTS) return true
        }
        return false
    }

    private fun substituteMacro(root: PsiElement, subst: WithParent): Pair<CharSequence, RangeMap>? {
        val sb = StringBuilder()
        val ranges = SmartList<MappedTextRange>()
        if (!substituteMacro(sb, ranges, root.node, subst)) return null
        return sb to RangeMap.from(ranges)
    }

    private fun substituteMacro(
        sb: StringBuilder,
        ranges: MutableList<MappedTextRange>,
        root: ASTNode,
        subst: WithParent
    ): Boolean {
        root.forEachChild { child ->
            when (child.elementType) {
                in RS_COMMENTS -> Unit
                MACRO_EXPANSION, MACRO_EXPANSION_CONTENTS ->
                    if (!substituteMacro(sb, ranges, child, subst)) return false
                MACRO_REFERENCE -> {
                    val value = subst.getVar((child.psi as RsMacroReference).referenceName)
                    if (value == null) {
                        MacroExpansionMarks.substMetaVarNotFound.hit()
                        sb.safeAppend(child.text)
                        return@forEachChild
                    }
                    val parensNeeded = value.kind == FragmentKind.Expr
                    if (parensNeeded) {
                        sb.append("(")
                        sb.append(value.value)
                        sb.append(")")
                    } else {
                        sb.safeAppend(value.value)
                    }
                    if (value.offsetInCallBody != -1 && value.value.isNotEmpty()) {
                        ranges.mergeAdd(MappedTextRange(
                            value.offsetInCallBody,
                            sb.length - value.value.length - if (parensNeeded) 1 else 0,
                            value.value.length
                        ))
                    }
                }
                MACRO_EXPANSION_REFERENCE_GROUP -> {
                    val childPsi = child.psi as RsMacroExpansionReferenceGroup
                    childPsi.macroExpansionContents?.let { contents ->
                        val separator = childPsi.macroExpansionGroupSeparator?.text ?: ""

                        val matchedGroup = subst.groups.singleOrNull()
                            ?: subst.groups.firstOrNull { it.definition.matches(contents) }
                            ?: return false

                        matchedGroup.substs.joinToWithBuffer(sb, separator) { sb ->
                            if (!substituteMacro(sb, ranges, contents.node, WithParent(this, subst))) return false
                        }
                    }
                }
                else -> {
//                    ranges += MappedTextRange(child.offsetInDefBody, sb.length, child.textLength, SourceType.MACRO_DEFINITION)
                    sb.safeAppend(child.chars)
                }
            }
        }
        return true
    }

    /** Ensures that the buffer ends (or [str] starts) with a whitespace and appends [str] to the buffer */
    private fun StringBuilder.safeAppend(str: CharSequence) {
        if (!isEmpty() && !last().isWhitespace() && !str.isEmpty() && !str.first().isWhitespace()) {
            append(" ")
        }
        append(str)
    }

    private fun MutableList<MappedTextRange>.mergeAdd(range: MappedTextRange) {
        val last = lastOrNull() ?: run {
            add(range)
            return
        }

        if (last.srcEndOffset == range.srcOffset && last.dstEndOffset == range.dstOffset) {
            set(size - 1, MappedTextRange(
                last.srcOffset,
                last.dstOffset,
                last.length + range.length
            ))
        } else {
            add(range)
        }
    }
}

/**
 * Returns (synthetic) path from [call] to [def]'s crate
 * We can't just expand `$crate` to something like `::crate_name` because
 * we can pass a result of `$crate` expansion to another macro as a single identifier.
 *
 * Let's look at the example:
 * ```
 * // foo_crate
 * macro_rules! foo {
 *     () => { bar!($crate); } // $crate consumed as a single identifier by `bar!`
 * }
 * // bar_crate
 * macro_rules! bar {
 *     ($i:ident) => { fn f() { $i::some_item(); } }
 * }
 * // baz_crate
 * mod baz {
 *     foo!();
 * }
 * ```
 * Imagine that macros `foo`, `bar` and the foo's call are located in different
 * crates. In this case, when expanding `foo!()`, we should expand $crate to
 * `::foo_crate`. This `::` is needed to make the path to the crate guaranteed
 * absolutely (and also to make it work on rust 2015 edition).
 * Ok, let's look at the result of single step of `foo` macro expansion:
 * `foo!()` => `bar!(::foo_crate)`. Syntactic construction `::foo_crate` consists
 * of 2 tokens: `::` and `foo_crate` (identifier). BUT `bar` expects a single
 * identifier as an input! And this is successfully complied by the rust compiler!!
 *
 * The secret is that we should not really expand `$crate` to `::foo_crate`.
 * We should expand it to "something" that can be passed to another macro
 * as a single identifier.
 *
 * Rustc figures it out by synthetic token (without text representation).
 * Rustc can do it this way because its macro substitution is based on AST.
 * But our expansion is text-based, so we must provide something textual
 * that can be parsed as an identifier.
 *
 * It's a very awful hack and we know it.
 * DON'T TRY THIS AT HOME
 */
private fun expandDollarCrateVar(call: RsMacroCall, def: RsMacro): String {
    val defTarget = def.containingCargoTarget
    val callTarget = call.containingCargoTarget
    val crateName = if (defTarget == callTarget) "self" else defTarget?.normName ?: ""
    return MACRO_CRATE_IDENTIFIER_PREFIX + crateName
}

/** Prefix for synthetic identifier produced from `$crate` metavar. See [expandDollarCrateVar] */
const val MACRO_CRATE_IDENTIFIER_PREFIX: String = "IntellijRustDollarCrate_"

class MacroPattern private constructor(
    val pattern: Sequence<ASTNode>
) {
    fun match(macroCallBody: PsiBuilder): MacroSubstitution? {
        return matchPartial(macroCallBody)?.let { result ->
            if (!macroCallBody.eof()) {
                MacroExpansionMarks.failMatchPatternByExtraInput.hit()
                null
            } else {
                result
            }
        }
    }

    private fun isEmpty() = pattern.firstOrNull() == null

    private fun matchPartial(macroCallBody: PsiBuilder): MacroSubstitution? {
        ProgressManager.checkCanceled()
        val map = HashMap<String, MetaVarValue>()
        val groups = mutableListOf<MacroGroup>()

        for (node in pattern) {
            when (node.elementType) {
                MACRO_BINDING -> {
                    val psi = node.psi as RsMacroBinding
                    val name = psi.metaVarIdentifier.text ?: return null
                    val fragmentSpecifier = psi.fragmentSpecifier ?: return null
                    val kind = FragmentKind.fromString(fragmentSpecifier) ?: return null

                    val lastOffset = macroCallBody.currentOffset
                    val parsed = kind.parse(macroCallBody)
                    if (!parsed || (lastOffset == macroCallBody.currentOffset && kind != FragmentKind.Vis)) {
                        MacroExpansionMarks.failMatchPatternByBindingType.hit()
                        return null
                    }
                    val text = macroCallBody.originalText.substring(lastOffset, macroCallBody.currentOffset)
                    map[name] = MetaVarValue(text, kind, lastOffset)
                }
                MACRO_BINDING_GROUP -> {
                    val psi = node.psi as RsMacroBindingGroup
                    groups += MacroGroup(psi, matchGroup(psi, macroCallBody) ?: return null)
                }
                else -> {
                    if (!macroCallBody.isSameToken(node)) {
                        MacroExpansionMarks.failMatchPatternByToken.hit()
                        return null
                    }
                }
            }
        }
        return MacroSubstitution(map, groups)
    }


    private fun matchGroup(group: RsMacroBindingGroup, macroCallBody: PsiBuilder): List<MacroSubstitution>? {
        val groups = mutableListOf<MacroSubstitution>()
        val pattern = MacroPattern.valueOf(group.macroPatternContents ?: return null)
        if (pattern.isEmpty()) return null
        val separator = group.macroBindingGroupSeparator?.node?.firstChildNode
        var mark: PsiBuilder.Marker? = null

        while (true) {
            if (macroCallBody.eof()) {
                MacroExpansionMarks.groupInputEnd1.hit()
                mark?.rollbackTo()
                break
            }

            val lastOffset = macroCallBody.currentOffset
            val result = pattern.matchPartial(macroCallBody)
            if (result != null) {
                if (macroCallBody.currentOffset == lastOffset) {
                    MacroExpansionMarks.groupMatchedEmptyTT.hit()
                    return null
                }
                groups += result
            } else {
                MacroExpansionMarks.groupInputEnd2.hit()
                mark?.rollbackTo()
                break
            }

            if (macroCallBody.eof()) {
                MacroExpansionMarks.groupInputEnd3.hit()
                // Don't need to roll the marker back: we just successfully parsed a group
                break
            }

            if (group.q != null) {
                // `$(...)?` means "0 or 1 occurrences"
                MacroExpansionMarks.questionMarkGroupEnd.hit()
                break
            }

            if (separator != null) {
                mark?.drop()
                mark = macroCallBody.mark()
                if (!macroCallBody.isSameToken(separator)) {
                    MacroExpansionMarks.failMatchGroupBySeparator.hit()
                    break
                }
            }
        }

        val isExpectedAtLeastOne = group.plus != null
        if (isExpectedAtLeastOne && groups.isEmpty()) {
            MacroExpansionMarks.failMatchGroupTooFewElements.hit()
            return null
        }

        return groups
    }

    companion object {
        fun valueOf(psi: RsMacroPatternContents): MacroPattern =
            MacroPattern(psi.node.childrenSkipWhitespaceAndComments().flatten())

        private fun Sequence<ASTNode>.flatten(): Sequence<ASTNode> = flatMap {
            when (it.elementType) {
                MACRO_PATTERN, MACRO_PATTERN_CONTENTS ->
                    it.childrenSkipWhitespaceAndComments().flatten()
                else -> sequenceOf(it)
            }
        }

        private fun ASTNode.childrenSkipWhitespaceAndComments(): Sequence<ASTNode> =
            generateSequence(firstChildNode) { it.treeNext }
                .filter { it.elementType != TokenType.WHITE_SPACE && it.elementType !in RS_COMMENTS }
    }
}

private val RsMacroCase.pattern: MacroPattern
    get() = MacroPattern.valueOf(macroPattern.macroPatternContents)

private val COMPARE_BY_TEXT_TOKES = TokenSet.orSet(tokenSetOf(IDENTIFIER, QUOTE_IDENTIFIER), RS_LITERALS)

fun PsiBuilder.isSameToken(node: ASTNode): Boolean {
    val (elementType, size) = collapsedTokenType(this) ?: (tokenType to 1)
    val result = node.elementType == elementType && (elementType !in COMPARE_BY_TEXT_TOKES || node.chars == tokenText)
    if (result) {
        PsiBuilderUtil.advance(this, size)
    }
    return result
}

private fun RsMacroBindingGroup.matches(contents: RsMacroExpansionContents): Boolean {
    val available = availableVars
    val used = contents.descendantsOfType<RsMacroReference>()
    return used.all { it.referenceName in available || it.referenceName == "crate" }
}

/**
 * Metavars available inside this group. Includes vars from this group, from all descendant groups
 * and from all ancestor groups. Sibling groups are excluded. E.g these vars available for these groups
 * ```
 * ($a [$b $($c)* ]    $($d)*      $($e           $($f)*)*)
 *         ^ a, b, c   ^ a, b, d   ^ a, b, e, f   ^ a, b, e, f
 * ```
 */
private val RsMacroBindingGroup.availableVars: Set<String>
    get() = CachedValuesManager.getCachedValue(this) {
        CachedValueProvider.Result.create(
            collectAvailableVars(this),
            ancestorStrict<RsMacro>()!!.modificationTracker
        )
    }

private fun collectAvailableVars(groupDefinition: RsMacroBindingGroup): Set<String> {
    val vars = groupDefinition.descendantsOfType<RsMacroBinding>().toMutableList()

    fun go(psi: PsiElement) {
        when (psi) {
            is RsMacroBinding -> vars += psi
            !is RsMacroBindingGroup -> psi.forEachChild(::go)
        }
    }

    groupDefinition.ancestors
        .drop(1)
        .takeWhile { it !is RsMacroCase}
        .forEach(::go)

    return vars.mapNotNullToSet { it.name }
}

object MacroExpansionMarks {
    val failMatchPatternByToken = Testmark("failMatchPatternByToken")
    val failMatchPatternByExtraInput = Testmark("failMatchPatternByExtraInput")
    val failMatchPatternByBindingType = Testmark("failMatchPatternByBindingType")
    val failMatchGroupBySeparator = Testmark("failMatchGroupBySeparator")
    val failMatchGroupTooFewElements = Testmark("failMatchGroupTooFewElements")
    val questionMarkGroupEnd = Testmark("questionMarkGroupEnd")
    val groupInputEnd1 = Testmark("groupInputEnd1")
    val groupInputEnd2 = Testmark("groupInputEnd2")
    val groupInputEnd3 = Testmark("groupInputEnd3")
    val groupMatchedEmptyTT = Testmark("groupMatchedEmptyTT")
    val substMetaVarNotFound = Testmark("substMetaVarNotFound")
    val docsLowering = Testmark("docsLowering")
}
