/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.decl

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderUtil
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.util.SmartList
import org.rust.lang.core.macros.*
import org.rust.lang.core.macros.errors.DeclMacroExpansionError
import org.rust.lang.core.macros.errors.MacroMatchingError
import org.rust.lang.core.macros.errors.MacroMatchingError.*
import org.rust.lang.core.parser.RustParserUtil.collapsedTokenType
import org.rust.lang.core.parser.createAdaptedRustPsiBuilder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.ext.fragmentSpecifier
import org.rust.openapiext.Testmark
import org.rust.openapiext.forEachChild
import org.rust.openapiext.isUnitTestMode
import org.rust.stdext.*
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok
import java.util.Collections.singletonMap

/**
 * The actual algorithm for expansion is not too hard, but is pretty tricky.
 * `MacroSubstitution` structure is the key to understanding what we are doing here.
 *
 * On the high level, it stores mapping from meta variables to the bits of
 * syntax it should be substituted with. For example, if `$e:expr` is matched
 * with `1 + 1` by macro_rules, the `MacroSubstitution` will store `$e -> 1 + 1`.
 *
 * The tricky bit is dealing with repetitions (`$()*`). Consider this example:
 *
 * ```
 * macro_rules! foo {
 *     ($($ i:ident $($ e:expr),*);*) => {
 *         $(fn $ i() { $($ e);*; })*
 *     }
 * }
 * foo! { foo 1,2,3; bar 4,5,6 }
 * ```
 *
 * Here, the `$i` meta variable is matched first with `foo` and then with
 * `bar`, and `$e` is matched in turn with `1`, `2`, `3`, `4`, `5`, `6`.
 *
 * To represent such "multi-mappings", we use a recursive structures: we map
 * variables not to values, but to *lists* of values or other lists (that is,
 * to the trees).
 *
 * For the above example, the MacroSubstitution would store
 *
 * ```
 * i -> [foo, bar]
 * e -> [[1, 2, 3], [4, 5, 6]]
 * ```
 *
 * The comment copied from [rust-analyzer](https://github.com/rust-analyzer/rust-analyzer/blob/2dd8ba2b21/crates/ra_mbe/src/mbe_expander.rs#L58)
 */
data class MacroSubstitution(
    val variables: Map<String, MetaVarValue>
)

private fun MacroSubstitution.getVar(name: String, nesting: List<NestingState>): VarLookupResult {
    var value = variables[name] ?: return VarLookupResult.None
    loop@ for (nestingState in nesting) {
        nestingState.hit = true
        value = when (value) {
            is MetaVarValue.Fragment -> break@loop
            is MetaVarValue.Group -> {
                value.nested.getOrNull(nestingState.idx) ?: run {
                    nestingState.atTheEnd = true
                    return VarLookupResult.Error
                }
            }
            MetaVarValue.EmptyGroup -> {
                nestingState.atTheEnd = true
                return VarLookupResult.Error
            }
        }
    }
    return when (value) {
        is MetaVarValue.Fragment -> VarLookupResult.Ok(value)
        is MetaVarValue.Group -> VarLookupResult.Error
        MetaVarValue.EmptyGroup -> VarLookupResult.Error
    }
}

private sealed class VarLookupResult {
    class Ok(val value: MetaVarValue.Fragment) : VarLookupResult()
    object None : VarLookupResult()
    object Error : VarLookupResult()
}

sealed class MetaVarValue {
    data class Fragment(
        val value: String,
        val kind: FragmentKind?,
        val elementType: IElementType?,
        val offsetInCallBody: Int
    ) : MetaVarValue()

    data class Group(val nested: MutableList<MetaVarValue> = mutableListOf()) : MetaVarValue()
    object EmptyGroup : MetaVarValue()
}

private class NestingState(
    var idx: Int = 0,
    var hit: Boolean = false,
    var atTheEnd: Boolean = false
)

class DeclMacroExpander(val project: Project): MacroExpander<RsDeclMacroData, DeclMacroExpansionError>() {
    override fun expandMacroAsTextWithErr(
        def: RsDeclMacroData,
        call: RsMacroCallData
    ): RsResult<Pair<CharSequence, RangeMap>, DeclMacroExpansionError> {
        val (case, subst, loweringRanges) = findMatchingPattern(def, call).unwrapOrElse { return Err(it) }
        val macroExpansion = case.macroExpansion?.macroExpansionContents ?: return Err(DeclMacroExpansionError.DefSyntax)

        val substWithGlobalVars = MacroSubstitution(
            subst.variables + singletonMap("crate", MetaVarValue.Fragment(MACRO_DOLLAR_CRATE_IDENTIFIER, null, null, -1))
        )

        val result = substituteMacro(macroExpansion, substWithGlobalVars).map { (text, ranges) ->
            text to loweringRanges.mapAll(ranges)
        }.unwrapOrElse { return Err(it) }

        checkRanges(call, result.first, result.second)

        return Ok(result)
    }

    private fun findMatchingPattern(
        def: RsDeclMacroData,
        call: RsMacroCallData
    ): RsResult<MatchedPattern, DeclMacroExpansionError> {
        val macroCallBodyText = (call.macroBody as? MacroCallBody.FunctionLike)?.text ?: return Err(DeclMacroExpansionError.DefSyntax)
        val (macroCallBody, ranges) = project
            .createAdaptedRustPsiBuilder(macroCallBodyText)
            .lowerDocCommentsToAdaptedPsiBuilder(project)
        macroCallBody.eof() // skip whitespace
        var start = macroCallBody.mark()
        val macroCaseList = def.macroBody.value?.macroCaseList ?: return Err(DeclMacroExpansionError.DefSyntax)

        val errors = mutableListOf<MacroMatchingError>()

        for (case in macroCaseList) {
            when (val result = case.pattern.match(macroCallBody)) {
                is Ok -> return Ok(MatchedPattern(case, result.ok, ranges))
                is Err -> {
                    errors += result.err // TODO map offset using `ranges`
                    start.rollbackTo()
                    start = macroCallBody.mark()
                }
            }
        }

        return Err(DeclMacroExpansionError.Matching(errors))
    }

    private data class MatchedPattern(val case: RsMacroCase, val subst: MacroSubstitution, val ranges: RangeMap)

    private class MacroStringBuilder {
        val inner: StringBuilder = StringBuilder()
        private var lastFragment: MetaVarValue.Fragment? = null

        val length: Int get() = inner.length

        fun append(s: String) {
            if (s.isEmpty()) return
            inner.append(s)
            lastFragment = null
        }

        /** Ensures that the buffer ends (or [str] starts) with a whitespace and appends [str] to the buffer */
        fun safeAppend(str: CharSequence) {
            if (inner.isNotEmpty() && !inner.last().isWhitespace() && str.isNotEmpty() && !str.first().isWhitespace()) {
                inner.append(' ')
            }
            inner.append(str)
            lastFragment = null
        }

        fun safeAppend(fragment: MetaVarValue.Fragment) {
            val lastFragment = lastFragment
            if (lastFragment != null && lastFragment.offsetInCallBody + lastFragment.value.length == fragment.offsetInCallBody) {
                // fragments were adjacent in macro call body - no need to add whitespace
                inner.append(fragment.value)
            } else {
                safeAppend(fragment.value)
            }
            this.lastFragment = fragment
        }

        fun delete(start: Int, end: Int) {
            if (start == end) return
            inner.delete(start, end)
            lastFragment = null
        }
    }

    private fun substituteMacro(root: PsiElement, subst: MacroSubstitution): RsResult<Pair<CharSequence, RangeMap>, DeclMacroExpansionError> {
        val sb = MacroStringBuilder()
        val ranges = SmartList<MappedTextRange>()
        if (!substituteMacro(sb, ranges, root.node, subst, mutableListOf())) {
            if (sb.length > FileUtilRt.LARGE_FOR_CONTENT_LOADING) {
                return Err(DeclMacroExpansionError.TooLargeExpansion)
            }
            return Err(DeclMacroExpansionError.DefSyntax)
        }
        return Ok(sb.inner to RangeMap(ranges))
    }

    private fun substituteMacro(
        sb: MacroStringBuilder,
        ranges: MutableList<MappedTextRange>,
        root: ASTNode,
        subst: MacroSubstitution,
        nesting: MutableList<NestingState>
    ): Boolean {
        if (sb.length > FileUtilRt.LARGE_FOR_CONTENT_LOADING) return false

        root.forEachChild { child ->
            when (child.elementType) {
                in RS_REGULAR_COMMENTS -> Unit
                MACRO_EXPANSION, MACRO_EXPANSION_CONTENTS ->
                    if (!substituteMacro(sb, ranges, child, subst, nesting)) return false
                MACRO_REFERENCE -> {
                    when (val result = subst.getVar((child.psi as RsMacroReference).referenceName, nesting)) {
                        is VarLookupResult.Ok -> {
                            val value = result.value
                            val parensNeeded = value.kind == FragmentKind.Expr
                                && value.elementType !in USELESS_PARENS_EXPRS
                            if (parensNeeded) {
                                sb.append("(")
                                sb.append(value.value)
                                sb.append(")")
                            } else {
                                sb.safeAppend(value)
                            }
                            if (value.offsetInCallBody != -1 && value.value.isNotEmpty()) {
                                ranges.mergeAdd(
                                    MappedTextRange(
                                    value.offsetInCallBody,
                                    sb.length - value.value.length - if (parensNeeded) 1 else 0,
                                    value.value.length
                                )
                                )
                            }
                        }
                        VarLookupResult.None -> {
                            MacroExpansionMarks.SubstMetaVarNotFound.hit()
                            sb.safeAppend(child.text)
                            return@forEachChild
                        }
                        VarLookupResult.Error -> {
                            return true
                        }
                    }
                }
                MACRO_EXPANSION_REFERENCE_GROUP -> {
                    val childPsi = child.psi as RsMacroExpansionReferenceGroup
                    childPsi.macroExpansionContents?.let { contents ->
                        nesting += NestingState()
                        val separator = childPsi.macroExpansionGroupSeparator?.text ?: ""

                        for (i in 0 until 65536) {
                            val lastPosition = sb.length
                            if (!substituteMacro(sb, ranges, contents.node, subst, nesting)) return false
                            val nestingState = nesting.last()
                            if (nestingState.atTheEnd || !nestingState.hit) {
                                sb.delete(lastPosition, sb.length)
                                if (i != 0) {
                                    sb.delete(sb.length - separator.length, sb.length)
                                }
                                while (ranges.isNotEmpty() && ranges.last().dstOffset >= sb.length) {
                                    ranges.removeLast()
                                }
                                break
                            }
                            nestingState.idx += 1
                            nestingState.hit = false
                            sb.append(separator)
                        }

                        nesting.removeLast()
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

    private fun checkRanges(call: RsMacroCallData, expandedText: CharSequence, ranges: RangeMap) {
        if (!isUnitTestMode) return
        val callBody = (call.macroBody as? MacroCallBody.FunctionLike)?.text ?: return

        for (range in ranges.ranges) {
            val callBodyFragment = callBody.subSequence(range.srcOffset, range.srcEndOffset)
            val expandedFragment = expandedText.subSequence(range.dstOffset, range.dstEndOffset)
            check(callBodyFragment == expandedFragment) {
                "`$callBodyFragment` != `$expandedFragment`"
            }
        }
    }

    companion object {
        const val EXPANDER_VERSION = 17
        private val USELESS_PARENS_EXPRS = tokenSetOf(
            LIT_EXPR, MACRO_EXPR, PATH_EXPR, PAREN_EXPR, TUPLE_EXPR, ARRAY_EXPR, UNIT_EXPR
        )
    }
}

/**
 * A synthetic identifier produced from `$crate` metavar expansion.
 *
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
const val MACRO_DOLLAR_CRATE_IDENTIFIER: String = "IntellijRustDollarCrate"
val MACRO_DOLLAR_CRATE_IDENTIFIER_REGEX: Regex = Regex(MACRO_DOLLAR_CRATE_IDENTIFIER)

typealias MacroMatchingResult = RsResult<MacroSubstitution, MacroMatchingError>
typealias GroupMatchingResult = RsResult<List<MacroSubstitution>, MacroMatchingError>

class MacroPattern private constructor(
    val pattern: Sequence<ASTNode>
) {
    fun match(macroCallBody: PsiBuilder): MacroMatchingResult {
        return matchPartial(macroCallBody).andThen { result ->
            if (!macroCallBody.eof()) {
                MacroExpansionMarks.FailMatchPatternByExtraInput.hit()
                err(::ExtraInput, macroCallBody)
            } else {
                Ok(result)
            }
        }
    }

    private fun isEmpty() = pattern.firstOrNull() == null

    private fun matchPartial(macroCallBody: PsiBuilder): MacroMatchingResult {
        ProgressManager.checkCanceled()
        val map = HashMap<String, MetaVarValue>()

        for (node in pattern) {
            when (node.elementType) {
                MACRO_BINDING -> {
                    val psi = node.psi as RsMacroBinding
                    val name = psi.metaVarIdentifier.text ?: return err(::PatternSyntax, macroCallBody)
                    val fragmentSpecifier = psi.fragmentSpecifier ?: return err(::PatternSyntax, macroCallBody)
                    val kind = FragmentKind.fromString(fragmentSpecifier) ?: return err(::PatternSyntax, macroCallBody)

                    val lastOffset = macroCallBody.currentOffset
                    val parsed = kind.parse(macroCallBody)
                    if (!parsed || (lastOffset == macroCallBody.currentOffset && kind != FragmentKind.Vis)) {
                        MacroExpansionMarks.FailMatchPatternByBindingType.hit()
                        return err(::FragmentIsNotParsed, macroCallBody, name, kind)
                    }
                    val text = macroCallBody.originalText.substring(lastOffset, macroCallBody.currentOffset)
                    val elementType = macroCallBody.latestDoneMarker?.tokenType
                    map[name] = MetaVarValue.Fragment(text, kind, elementType, lastOffset)
                }
                MACRO_BINDING_GROUP -> {
                    val psi = node.psi as RsMacroBindingGroup
                    val nesteds = matchGroup(psi, macroCallBody).unwrapOrElse { return Err(it) }
                    nesteds.forEachIndexed { i, nested ->
                        for ((key, value) in nested.variables) {
                            val it = map.getOrPut(key) { MetaVarValue.Group() } as? MetaVarValue.Group
                                ?: return err(::Nesting, macroCallBody, key)
                            while (it.nested.size < i) {
                                it.nested += MetaVarValue.Group()
                            }
                            it.nested += value
                        }
                    }
                    if (nesteds.isEmpty()) {
                        val vars = psi.descendantsOfType<RsMacroBinding>().mapNotNullToSet { it.name }
                        for (v in vars) {
                            map[v] = MetaVarValue.EmptyGroup
                        }
                    }
                }
                else -> {
                    if (!macroCallBody.isSameToken(node)) {
                        MacroExpansionMarks.FailMatchPatternByToken.hit()
                        val actualToken = macroCallBody.tokenType?.toString()
                        val actualTokenText = macroCallBody.tokenText
                        return if (actualToken != null && actualTokenText != null) {
                            Err(
                                UnmatchedToken(
                                    macroCallBody.currentOffset,
                                    node.elementType.toString(),
                                    node.text,
                                    actualToken,
                                    actualTokenText
                                )
                            )
                        } else {
                            err(::EndOfInput, macroCallBody)
                        }
                    }
                }
            }
        }
        return Ok(MacroSubstitution(map))
    }

    private fun matchGroup(group: RsMacroBindingGroup, macroCallBody: PsiBuilder): GroupMatchingResult {
        val groups = mutableListOf<MacroSubstitution>()
        val pattern = valueOf(group.macroPatternContents ?: return err(::PatternSyntax, macroCallBody))
        if (pattern.isEmpty()) return err(::PatternSyntax, macroCallBody)
        val separator = group.macroBindingGroupSeparator?.node?.firstChildNode
        macroCallBody.eof() // skip whitespace
        var mark: PsiBuilder.Marker? = macroCallBody.mark()

        while (true) {
            if (macroCallBody.eof()) {
                MacroExpansionMarks.GroupInputEnd1.hit()
                mark?.rollbackTo()
                break
            }

            val lastOffset = macroCallBody.currentOffset
            val result = pattern.matchPartial(macroCallBody)
            if (result is Ok) {
                if (macroCallBody.currentOffset == lastOffset) {
                    MacroExpansionMarks.GroupMatchedEmptyTT.hit()
                    return err(::EmptyGroup, macroCallBody)
                }
                groups += result.ok
            } else {
                MacroExpansionMarks.GroupInputEnd2.hit()
                mark?.rollbackTo()
                break
            }

            if (macroCallBody.eof()) {
                MacroExpansionMarks.GroupInputEnd3.hit()
                // Don't need to roll the marker back: we just successfully parsed a group
                break
            }

            if (group.q != null) {
                // `$(...)?` means "0 or 1 occurrences"
                MacroExpansionMarks.QuestionMarkGroupEnd.hit()
                break
            }

            mark?.drop()
            mark = macroCallBody.mark()

            if (separator != null) {
                if (!macroCallBody.isSameToken(separator)) {
                    MacroExpansionMarks.FailMatchGroupBySeparator.hit()
                    break
                }
            }
        }

        val isExpectedAtLeastOne = group.plus != null
        if (isExpectedAtLeastOne && groups.isEmpty()) {
            MacroExpansionMarks.FailMatchGroupTooFewElements.hit()
            return err(::TooFewGroupElements, macroCallBody)
        }

        return Ok(groups)
    }

    private fun <E> err(
        ctor: (offsetInCallBody: Int) -> E,
        macroCallBody: PsiBuilder
    ): Err<E> {
        return Err(ctor(macroCallBody.currentOffset))
    }

    private fun <E, P1> err(
        ctor: (offsetInCallBody: Int, P1) -> E,
        macroCallBody: PsiBuilder,
        p1: P1
    ): Err<E> {
        return Err(ctor(macroCallBody.currentOffset, p1))
    }

    private fun <E, P1, P2> err(
        ctor: (offsetInCallBody: Int, P1, P2) -> E,
        macroCallBody: PsiBuilder,
        p1: P1,
        p2: P2
    ): Err<E> {
        return Err(ctor(macroCallBody.currentOffset, p1, p2))
    }

    companion object {
        fun valueOf(psi: RsMacroPatternContents?): MacroPattern =
            MacroPattern(psi?.node?.childrenSkipWhitespaceAndComments()?.flatten() ?: emptySequence())

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
    val result = node.elementType == elementType
        && (elementType !in COMPARE_BY_TEXT_TOKES || GeneratedParserUtilBase.nextTokenIsFast(this, node.text, true) == size)
    if (result) {
        PsiBuilderUtil.advance(this, size)
    }
    return result
}

object MacroExpansionMarks {
    object FailMatchPatternByToken : Testmark()
    object FailMatchPatternByExtraInput : Testmark()
    object FailMatchPatternByBindingType : Testmark()
    object FailMatchGroupBySeparator : Testmark()
    object FailMatchGroupTooFewElements : Testmark()
    object QuestionMarkGroupEnd : Testmark()
    object GroupInputEnd1 : Testmark()
    object GroupInputEnd2 : Testmark()
    object GroupInputEnd3 : Testmark()
    object GroupMatchedEmptyTT : Testmark()
    object SubstMetaVarNotFound : Testmark()
    object DocsLowering : Testmark()
}
