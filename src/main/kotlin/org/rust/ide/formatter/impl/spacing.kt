/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter.impl

import com.intellij.formatting.ASTBlock
import com.intellij.formatting.Block
import com.intellij.formatting.Spacing
import com.intellij.formatting.Spacing.createKeepingFirstColumnSpacing
import com.intellij.formatting.Spacing.createSpacing
import com.intellij.formatting.SpacingBuilder
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.rust.ide.formatter.RsFmtContext
import org.rust.ide.formatter.settings.RsCodeStyleSettings
import org.rust.lang.core.parser.RustParserDefinition
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.getNextNonCommentSibling
import org.rust.lang.core.psi.ext.getPrevNonCommentSibling
import com.intellij.psi.tree.TokenSet.create as ts

fun createSpacingBuilder(commonSettings: CommonCodeStyleSettings, rustSettings: RsCodeStyleSettings): SpacingBuilder {

    // Use `sbX` temporaries to work around
    // https://youtrack.jetbrains.com/issue/KT-12239

    val sb1 = SpacingBuilder(commonSettings)
        // Rules defined earlier have higher priority.
        // Beware of comments between blocks!

        //== some special operators
        // FIXME(mkaput): Doesn't work well with comments
        .afterInside(COMMA, ts(BLOCK_FIELDS, ENUM_BODY)).parentDependentLFSpacing(1, 1, true, 1)
        .afterInside(COMMA, ts(BLOCK_FIELDS, STRUCT_LITERAL_BODY)).parentDependentLFSpacing(1, 1, true, 1)
        .after(COMMA).spacing(1, 1, 0, true, 0)
        .before(COMMA).spaceIf(false)
        .after(COLON).spaceIf(true)
        .before(COLON).spaceIf(false)
        .after(SEMICOLON).spaceIf(true)
        .before(SEMICOLON).spaceIf(false)
        .afterInside(AND, ts(REF_LIKE_TYPE, SELF_PARAMETER, PAT_REF, VALUE_PARAMETER)).spaces(0)
        .beforeInside(Q, TRY_EXPR).spaces(0)
        .afterInside(UNARY_OPS, UNARY_EXPR).spaces(0)
        // `use ::{bar}`
        .between(USE, COLONCOLON).spaces(1)

        //== attributes
        .aroundInside(ts(SHA, EXCL, LBRACK, RBRACK), ATTRS).spaces(0)
        .aroundInside(ts(LPAREN, RPAREN), META_ITEM_ARGS).spaces(0)
        .around(META_ITEM_ARGS).spaces(0)

        //== empty parens
        .between(LPAREN, RPAREN).spacing(0, 0, 0, false, 0)
        .between(LBRACK, RBRACK).spacing(0, 0, 0, false, 0)
        .between(LBRACE, RBRACE).spacing(0, 0, 0, false, 0)
        .betweenInside(OR, OR, LAMBDA_EXPR).spacing(0, 0, 0, false, 0)

        //== paren delimited lists
        // withinPairInside does not accept TokenSet as parent node set :(
        // and we cannot create our own, because RuleCondition stuff is private
        .afterInside(LPAREN, PAREN_LISTS).spacing(0, 0, 0, true, 0)
        .beforeInside(RPAREN, PAREN_LISTS).spacing(0, 0, 0, true, 0)
        .afterInside(LBRACK, BRACK_LISTS).spacing(0, 0, 0, true, 0)
        .beforeInside(RBRACK, BRACK_LISTS).spacing(0, 0, 0, true, 0)
        .afterInside(LBRACE, BRACE_LISTS).spacing(0, 0, 0, true, 0)
        .beforeInside(RBRACE, BRACE_LISTS).spacing(0, 0, 0, true, 0)
        .afterInside(LT, ANGLE_LISTS).spacing(0, 0, 0, true, 0)
        .beforeInside(GT, ANGLE_LISTS).spacing(0, 0, 0, true, 0)
        .aroundInside(OR, VALUE_PARAMETER_LIST).spacing(0, 0, 0, false, 0)

    val sb2 = sb1
        //== items
        .before(VIS_RESTRICTION).spaces(0) // pub(crate)
        .between(VALUE_PARAMETER_LIST, RET_TYPE).spacing(1, 1, 0, true, 0)
        .before(WHERE_CLAUSE).spacing(1, 1, 0, true, 0)
        .beforeInside(LBRACE, FLAT_BRACE_BLOCKS).spaces(1)

        .between(ts(IDENTIFIER, FN), VALUE_PARAMETER_LIST).spaceIf(false)
        .between(IDENTIFIER, TUPLE_FIELDS).spaces(0)
        .between(IDENTIFIER, TYPE_PARAMETER_LIST).spaceIf(false)
        .between(IDENTIFIER, TYPE_ARGUMENT_LIST).spaceIf(false)
        .between(IDENTIFIER, VALUE_ARGUMENT_LIST).spaceIf(false)
        .between(TYPE_PARAMETER_LIST, VALUE_PARAMETER_LIST).spaceIf(false)
        .before(VALUE_ARGUMENT_LIST).spaceIf(false)

        .between(BINDING_MODE, IDENTIFIER).spaces(1)
        .between(IMPL, TYPE_PARAMETER_LIST).spaces(0)
        .afterInside(TYPE_PARAMETER_LIST, IMPL_ITEM).spaces(1)
        .betweenInside(ts(TYPE_PARAMETER_LIST), TYPES, IMPL_ITEM).spaces(1)

        // Handling blocks is pretty complicated. Do not tamper with
        // them too much and let rustfmt do all the pesky work.
        // Some basic transformation from in-line block to multi-line block
        // is also performed; see doc of #blockMustBeMultiLine() for details.
        .afterInside(LBRACE, BLOCK_LIKE).parentDependentLFSpacing(1, 1, true, 0)
        .beforeInside(RBRACE, BLOCK_LIKE).parentDependentLFSpacing(1, 1, true, 0)
        .afterInside(LBRACE, FLAT_BRACE_BLOCKS).parentDependentLFSpacing(1, 1, true, 0)
        .beforeInside(RBRACE, FLAT_BRACE_BLOCKS).parentDependentLFSpacing(1, 1, true, 0)
        .withinPairInside(LBRACE, RBRACE, PAT_STRUCT).spacing(1, 1, 0, true, 0)

        .betweenInside(IDENTIFIER, ALIAS, EXTERN_CRATE_ITEM).spaces(1)

        .betweenInside(IDENTIFIER, TUPLE_FIELDS, ENUM_VARIANT).spaces(0)
        .betweenInside(IDENTIFIER, VARIANT_DISCRIMINANT, ENUM_VARIANT).spaces(1)

    return sb2
        //== types
        .afterInside(LIFETIME, REF_LIKE_TYPE).spaceIf(true)
        .betweenInside(ts(MUL), ts(CONST, MUT), REF_LIKE_TYPE).spaces(0)
        .before(TYPE_PARAM_BOUNDS).spaces(0)
        .beforeInside(LPAREN, PATH).spaces(0)
        .after(TYPE_QUAL).spaces(0)
        .betweenInside(FOR, LT, FOR_LIFETIMES).spacing(0, 0, 0, true, 0)
        .around(FOR_LIFETIMES).spacing(1, 1, 0, true, 0)
        .aroundInside(EQ, ASSOC_TYPE_BINDING).spaces(0)

        //== expressions
        .beforeInside(LPAREN, PAT_ENUM).spaces(0)
        .beforeInside(LBRACK, INDEX_EXPR).spaces(0)
        .afterInside(VALUE_PARAMETER_LIST, LAMBDA_EXPR).spacing(1, 1, 0, true, 1)
        .between(MATCH_ARM, MATCH_ARM).spacing(1, 1, if (rustSettings.ALLOW_ONE_LINE_MATCH) 0 else 1, true, 1)
        .before(ELSE_BRANCH).spacing(1, 1, 0, false, 0)
        .betweenInside(ELSE, BLOCK, ELSE_BRANCH).spacing(1, 1, 0, false, 0)

        //== macros
        .beforeInside(EXCL, MACRO_CALL).spaces(0)
        .beforeInside(EXCL, MACRO_DEFINITION).spaces(0)
        .afterInside(EXCL, MACRO_DEFINITION).spaces(1)
        .betweenInside(IDENTIFIER, MACRO_DEFINITION_BODY, MACRO_DEFINITION).spaces(1)

        //== rules with very large area of application
        .around(NO_SPACE_AROUND_OPS).spaces(0)
        .around(BINARY_OP).spaces(1)
        .around(SPACE_AROUND_OPS).spaces(1)
        .around(RS_KEYWORDS).spaces(1)
        .applyForEach(BLOCK_LIKE) { before(it).spaces(1) }
}

fun Block.computeSpacing(child1: Block?, child2: Block, ctx: RsFmtContext): Spacing? {
    if (child1 is ASTBlock && child2 is ASTBlock) SpacingContext.create(child1, child2, ctx).apply {
        when {
            elementType2 == RustParserDefinition.EOL_COMMENT ->
                return createKeepingFirstColumnSpacing(1, Int.MAX_VALUE, true, ctx.commonSettings.KEEP_BLANK_LINES_IN_CODE)

        // #[attr]\n<comment>\n => #[attr] <comment>\n etc.
            psi1 is RsOuterAttr && psi2 is PsiComment
            -> return createSpacing(1, 1, 0, true, 0)

        // Determine spacing between macro invocation and it's arguments
            parentPsi is RsMacroCall && elementType1 == EXCL
            -> return if (node2.chars.first() == '{' || elementType2 == IDENTIFIER) {
                createSpacing(1, 1, 0, false, 0)
            } else {
                createSpacing(0, 0, 0, false, 0)
            }

        // Ensure that each attribute is in separate line; comment aware
            psi1 is RsOuterAttr && (psi2 is RsOuterAttr || psi1.parent is RsItemElement)
                || psi1 is PsiComment && (psi2 is RsOuterAttr || psi1.getPrevNonCommentSibling() is RsOuterAttr)
            -> return lineBreak(keepBlankLines = 0)

        // Format blank lines between statements (or return expression)
            ncPsi1 is RsStmt && ncPsi2.isStmtOrExpr
            -> return lineBreak(
                keepLineBreaks = ctx.commonSettings.KEEP_LINE_BREAKS,
                keepBlankLines = ctx.commonSettings.KEEP_BLANK_LINES_IN_CODE)

        // Format blank lines between impl & trait members
            parentPsi is RsMembers && ncPsi1 is RsNamedElement && ncPsi2 is RsNamedElement
            -> return lineBreak(
                keepLineBreaks = ctx.commonSettings.KEEP_LINE_BREAKS,
                keepBlankLines = ctx.commonSettings.KEEP_BLANK_LINES_IN_DECLARATIONS)

        // Format blank lines between top level items
            ncPsi1.isTopLevelItem && ncPsi2.isTopLevelItem
            -> return lineBreak(
                minLineFeeds = 1 +
                    if (!needsBlankLineBetweenItems()) 0
                    else ctx.rustSettings.MIN_NUMBER_OF_BLANKS_BETWEEN_ITEMS,

                keepLineBreaks = ctx.commonSettings.KEEP_LINE_BREAKS,
                keepBlankLines = ctx.commonSettings.KEEP_BLANK_LINES_IN_DECLARATIONS)
        }
    }
    return ctx.spacingBuilder.getSpacing(this, child1, child2)
}

private data class SpacingContext(val node1: ASTNode,
                                  val node2: ASTNode,
                                  val psi1: PsiElement,
                                  val psi2: PsiElement,
                                  val elementType1: IElementType,
                                  val elementType2: IElementType,
                                  val parentType: IElementType?,
                                  val parentPsi: PsiElement?,
                                  val ncPsi1: PsiElement,
                                  val ncPsi2: PsiElement,
                                  val ctx: RsFmtContext) {
    companion object {
        fun create(child1: ASTBlock, child2: ASTBlock, ctx: RsFmtContext): SpacingContext {
            val node1 = child1.node
            val node2 = child2.node
            val psi1 = node1.psi
            val psi2 = node2.psi
            val elementType1 = psi1.node.elementType
            val elementType2 = psi2.node.elementType
            val parentType = node1.treeParent.elementType
            val parentPsi = psi1.parent
            val (ncPsi1, ncPsi2) = omitCommentBlocks(node1, psi1, node2, psi2)
            return SpacingContext(node1, node2, psi1, psi2, elementType1, elementType2,
                parentType, parentPsi, ncPsi1, ncPsi2, ctx)
        }

        /**
         * Handle blocks of comments to get proper spacing between items and statements
         */
        private fun omitCommentBlocks(node1: ASTNode, psi1: PsiElement,
                                      node2: ASTNode, psi2: PsiElement): Pair<PsiElement, PsiElement> =
            Pair(
                if (psi1 is PsiComment && node1.hasLineBreakAfterInSameParent()) {
                    psi1.getPrevNonCommentSibling() ?: psi1
                } else {
                    psi1
                },
                if (psi2 is PsiComment && node2.hasLineBreakBeforeInSameParent()) {
                    psi2.getNextNonCommentSibling() ?: psi2
                } else {
                    psi2
                }
            )
    }
}

private inline fun SpacingBuilder.applyForEach(
    tokenSet: TokenSet, block: SpacingBuilder.(IElementType) -> SpacingBuilder): SpacingBuilder {
    var self = this
    for (tt in tokenSet.types) {
        self = block(this, tt)
    }
    return self
}

private fun lineBreak(minLineFeeds: Int = 1,
                      keepLineBreaks: Boolean = true,
                      keepBlankLines: Int = 1): Spacing =
    createSpacing(0, Int.MAX_VALUE, minLineFeeds, keepLineBreaks, keepBlankLines)

private fun ASTNode.hasLineBreakAfterInSameParent(): Boolean =
    treeNext != null && TreeUtil.findFirstLeaf(treeNext).isWhiteSpaceWithLineBreak()

private fun ASTNode.hasLineBreakBeforeInSameParent(): Boolean =
    treePrev != null && TreeUtil.findLastLeaf(treePrev).isWhiteSpaceWithLineBreak()

private fun ASTNode?.isWhiteSpaceWithLineBreak(): Boolean =
    this != null && elementType == WHITE_SPACE && textContains('\n')

private fun SpacingContext.needsBlankLineBetweenItems(): Boolean {
    if (elementType1 in RS_COMMENTS || elementType2 in RS_COMMENTS)
        return false

    // Allow to keep consecutive runs of `use`, `const` or other "one line" items without blank lines
    if (elementType1 == elementType2 && elementType1 in ONE_LINE_ITEMS)
        return false

    // #![deny(missing_docs)
    // extern crate regex;
    if (elementType1 == INNER_ATTR && elementType2 == EXTERN_CRATE_ITEM)
        return false

    return true
}
