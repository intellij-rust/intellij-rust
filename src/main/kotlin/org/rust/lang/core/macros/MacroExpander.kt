/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderFactory
import com.intellij.lang.PsiBuilderUtil
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.DummyHolderFactory
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.RsLanguage
import org.rust.lang.core.parser.RustParser
import org.rust.lang.core.parser.RustParserUtil.collapsedTokenType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.Testmark
import org.rust.openapiext.forEachChild
import org.rust.stdext.joinToWithBuffer
import org.rust.stdext.mapNotNullToSet
import java.util.*
import java.util.Collections.singletonMap

private data class MacroSubstitution(
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
    val variables: Map<String, String>,

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

private data class MacroGroup(
    val definition: RsMacroBindingGroup,
    val substs: List<MacroSubstitution>
)

private data class WithParent(
    private val subst: MacroSubstitution,
    private val parent: WithParent?
) {
    val groups: List<MacroGroup>
        get() = subst.groups

    fun getVar(name: String): String? =
        subst.variables[name] ?: parent?.getVar(name)
}

private val STD_MACRO_WHITELIST = setOf("write", "writeln")

class MacroExpander(val project: Project) {
    private val psiFactory = RsPsiFactory(project)

    fun expandMacro(def: RsMacro, call: RsMacroCall): List<RsExpandedElement>? {
        // All std macros contain the only `impl`s which are not supported for now, so ignoring them
        if (def.containingCargoTarget?.pkg?.origin == PackageOrigin.STDLIB && def.name !in STD_MACRO_WHITELIST) {
            return null
        }

        val expandedText = expandMacroAsText(def, call) ?: return null

        return psiFactory.parseExpandedTextWithContext(call, expandedText)
    }

    private fun expandMacroAsText(def: RsMacro, call: RsMacroCall): CharSequence? {
        val (case, subst) = findMatchingPattern(def, call) ?: return null
        val macroExpansion = case.macroExpansion ?: return null

        val crate = def.containingMod.crateRelativePath ?: ""
        val substWithGlobalVars = WithParent(
            subst,
            WithParent(
                MacroSubstitution(
                    singletonMap("crate", crate),
                    emptyList()
                ),
                null
            )
        )

        return substituteMacro(macroExpansion.macroExpansionContents, substWithGlobalVars)
    }

    private fun findMatchingPattern(
        def: RsMacro,
        call: RsMacroCall
    ): Pair<RsMacroCase, MacroSubstitution>? {
        val macroCallBody = createPsiBuilder(call, call.macroBody ?: return null)
        var start = macroCallBody.mark()
        val macroCaseList = def.macroBodyStubbed?.macroCaseList ?: return null

        for (case in macroCaseList) {
            val subst = case.pattern.match(macroCallBody)
            if (subst != null) {
                return case to subst
            } else {
                start.rollbackTo()
                start = macroCallBody.mark()
            }
        }

        return null
    }

    private fun createPsiBuilder(context: PsiElement, text: String): PsiBuilder {
        val holder = DummyHolderFactory.createHolder(PsiManager.getInstance(project), context).treeElement
        val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(RsLanguage)
            ?: error("No parser definition for language $RsLanguage")
        val lexer = parserDefinition.createLexer(project)
        return PsiBuilderFactory.getInstance().createBuilder(project, holder, lexer, RsLanguage, text)
    }

    private fun substituteMacro(root: PsiElement, subst: WithParent): CharSequence? =
        buildString { if (!substituteMacro(this, root, subst)) return null }

    private fun substituteMacro(sb: StringBuilder, root: PsiElement, subst: WithParent): Boolean {
        val children = generateSequence(root.firstChild) { it.nextSibling }.filter { it !is PsiComment }
        for (child in children) {
            when (child) {
                is RsMacroExpansion, is RsMacroExpansionContents ->
                    if (!substituteMacro(sb, child, subst)) return false
                is RsMacroReference ->
                    sb.append(subst.getVar(child.referenceName) ?: return false)
                is RsMacroExpansionReferenceGroup -> {
                    child.macroExpansionContents?.let { contents ->
                        val separator = child.macroExpansionGroupSeparator?.text ?: ""

                        val matchedGroup = subst.groups.singleOrNull()
                            ?: subst.groups.firstOrNull { it.definition.matches(contents) }
                            ?: return false

                        matchedGroup.substs.joinToWithBuffer(sb, separator) { sb ->
                            if (!substituteMacro(sb, contents, WithParent(this, subst))) return false
                        }
                    }
                }
                else -> {
                    sb.append(" ")
                    sb.append(child.text)
                    sb.append(" ")
                }
            }
        }
        return true
    }

    private val RsMacro.macroBodyStubbed: RsMacroBody?
        get() {
            val stub = stub ?: return macroBody
            val text = stub.macroBody ?: return null
            return CachedValuesManager.getCachedValue(this) {
                CachedValueProvider.Result.create(
                    psiFactory.createMacroBody(text),
                    PsiModificationTracker.MODIFICATION_COUNT
                )
            }
        }
}

private class MacroPattern private constructor(
    val pattern: Sequence<PsiElement>
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
        val map = HashMap<String, String>()
        val groups = mutableListOf<MacroGroup>()

        for (psi in pattern) {
            when (psi) {
                is RsMacroBinding -> {
                    val name = psi.metaVarIdentifier.text ?: return null
                    val type = psi.fragmentSpecifier ?: return null

                    val lastOffset = macroCallBody.currentOffset
                    val parsed = parse(macroCallBody, type)
                    if (!parsed || lastOffset == macroCallBody.currentOffset) {
                        MacroExpansionMarks.failMatchPatternByBindingType.hit()
                        return null
                    }
                    val text = macroCallBody.originalText.substring(lastOffset, macroCallBody.currentOffset)

                    // Wrap expressions in () to avoid problems related to operator precedence during expansion
                    if (type == "expr")
                        map[name] = "($text)"
                    else
                        map[name] = text
                }
                is RsMacroBindingGroup -> {
                    groups += MacroGroup(psi, matchGroup(psi, macroCallBody) ?: return null)
                }
                else -> {
                    if (!macroCallBody.isSameToken(psi)) {
                        MacroExpansionMarks.failMatchPatternByToken.hit()
                        return null
                    }
                }
            }
        }
        return MacroSubstitution(map, groups)
    }

    private fun parse(builder: PsiBuilder, type: String): Boolean {
        return if (type == "ident") {
            parseIdentifier(builder)
        } else {
            // we use similar logic as in org.rust.lang.core.parser.RustParser#parseLight
            val root = RsElementTypes.FUNCTION
            val adaptBuilder = GeneratedParserUtilBase.adapt_builder_(
                root,
                builder,
                RustParser(),
                RustParser.EXTENDS_SETS_
            )
            val marker = GeneratedParserUtilBase
                .enter_section_(adaptBuilder, 0, GeneratedParserUtilBase._COLLAPSE_, null)

            val parsed = when (type) {
                "path" -> RustParser.PathGenericArgsWithColons(adaptBuilder, 0)
                "expr" -> RustParser.Expr(adaptBuilder, 0, -1)
                "ty" -> RustParser.TypeReference(adaptBuilder, 0)
                "pat" -> RustParser.Pat(adaptBuilder, 0)
                "stmt" -> parseStatement(adaptBuilder)
                "block" -> RustParser.SimpleBlock(adaptBuilder, 0)
                "item" -> parseItem(adaptBuilder)
                "meta" -> RustParser.MetaItemWithoutTT(adaptBuilder, 0)
                "tt" -> RustParser.TT(adaptBuilder, 0)
                else -> false
            }
            GeneratedParserUtilBase.exit_section_(adaptBuilder, 0, marker, root, parsed, true) { _, _ -> false }
            parsed
        }
    }

    private fun matchGroup(group: RsMacroBindingGroup, macroCallBody: PsiBuilder): List<MacroSubstitution>? {
        val groups = mutableListOf<MacroSubstitution>()
        val pattern = MacroPattern.valueOf(group.macroPatternContents ?: return null)
        if (pattern.isEmpty()) return null
        val separator = group.macroBindingGroupSeparator?.firstChild
        var mark: PsiBuilder.Marker? = null

        while (true) {
            if (macroCallBody.eof()) {
                MacroExpansionMarks.groupInputEnd1.hit()
                mark?.rollbackTo()
                break
            }

            val result = pattern.matchPartial(macroCallBody)
            if (result != null) {
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

            if (separator != null) {
                mark?.drop()
                mark = macroCallBody.mark()
                if (!macroCallBody.isSameToken(separator)) {
                    MacroExpansionMarks.failMatchGroupBySeparator.hit()
                    break
                }
            }
        }

        return groups
    }

    private fun parseIdentifier(b: PsiBuilder): Boolean {
        return if (b.tokenType in IDENTIFIER_TOKENS) {
            b.advanceLexer()
            true
        } else {
            false
        }
    }

    private fun parseStatement(b: PsiBuilder): Boolean =
        RustParser.LetDecl(b, 0) || RustParser.Expr(b, 0, -1)

    private fun parseItem(b: PsiBuilder): Boolean =
        parseItemFns.any { it(b, 0) }

    companion object {
        fun valueOf(psi: RsMacroPatternContents): MacroPattern =
            MacroPattern(psi.childrenSkipWhitespaceAndComments().flatten())

        private fun Sequence<PsiElement>.flatten(): Sequence<PsiElement> = flatMap {
            when (it) {
                is RsMacroPattern, is RsMacroPatternContents ->
                    it.childrenSkipWhitespaceAndComments().flatten()
                else -> sequenceOf(it)
            }
        }

        private fun PsiElement.childrenSkipWhitespaceAndComments(): Sequence<PsiElement> =
            generateSequence(firstChild) { it.nextSibling }
                .filter { it !is PsiWhiteSpace && it !is PsiComment }

        private val parseItemFns = listOf(
            RustParser::Constant,
            RustParser::TypeAlias,
            RustParser::Function,
            RustParser::TraitItem,
            RustParser::ImplItem,
            RustParser::ModItem,
            RustParser::ModDeclItem,
            RustParser::ForeignModItem,
            RustParser::StructItem,
            RustParser::EnumItem,
            RustParser::UseItem,
            RustParser::ExternCrateItem,
            RustParser::MacroCall
        )

        /**
         * Some tokens that treated as keywords by our lexer,
         * but rustc's macro parser treats them as identifiers
         */
        private val IDENTIFIER_TOKENS = TokenSet.create(
            RsElementTypes.IDENTIFIER,
            RsElementTypes.SELF,
            RsElementTypes.CSELF
        )
    }
}

private val RsMacroCase.pattern: MacroPattern
    get() = MacroPattern.valueOf(macroPattern.macroPatternContents)

private fun PsiBuilder.isSameToken(psi: PsiElement): Boolean {
    val (elementType, size) = collapsedTokenType(this) ?: (tokenType to 1)
    val result = psi.elementType == elementType && (elementType != IDENTIFIER || psi.text == tokenText)
    if (result) {
        PsiBuilderUtil.advance(this, size)
    }
    return result
}

private fun RsMacroBindingGroup.matches(contents: RsMacroExpansionContents): Boolean {
    val available = availableVars
    val used = contents.descendantsOfType<RsMacroReference>()
    return used.all { it.referenceName in available }
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
            PsiModificationTracker.MODIFICATION_COUNT // TODO use modtracker attached to the macro
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

fun RsPsiFactory.parseExpandedTextWithContext(call: RsMacroCall, expandedText: CharSequence): List<RsExpandedElement> =
    when (call.context) {
        is RsMacroExpr -> listOfNotNull(tryCreateExpression(expandedText))
        is RsPatMacro -> listOfNotNull(tryCreatePat(expandedText))
        is RsMacroType -> listOfNotNull(tryCreateType(expandedText))
        else -> createFile(expandedText).childrenOfType<RsExpandedElement>()
    }

object MacroExpansionMarks {
    val failMatchPatternByToken = Testmark("failMatchPatternByToken")
    val failMatchPatternByExtraInput = Testmark("failMatchPatternByExtraInput")
    val failMatchPatternByBindingType = Testmark("failMatchPatternByBindingType")
    val failMatchGroupBySeparator = Testmark("failMatchGroupBySeparator")
    val groupInputEnd1 = Testmark("groupInputEnd1")
    val groupInputEnd2 = Testmark("groupInputEnd2")
    val groupInputEnd3 = Testmark("groupInputEnd3")
}
