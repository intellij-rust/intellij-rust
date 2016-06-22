package org.rust.ide.formatter.impl

import com.intellij.formatting.ASTBlock
import com.intellij.formatting.Block
import com.intellij.formatting.Spacing
import com.intellij.formatting.Spacing.createSpacing
import com.intellij.formatting.SpacingBuilder
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.formatter.FormatterUtil
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.rust.ide.formatter.RustFmtContext
import org.rust.ide.formatter.settings.RustCodeStyleSettings
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RustCompositeElementTypes.*
import org.rust.lang.core.psi.RustTokenElementTypes.*
import org.rust.lang.core.psi.util.containsEOL
import org.rust.lang.core.psi.util.elementType
import org.rust.lang.core.psi.util.getNextNonCommentSibling
import org.rust.lang.core.psi.util.getPrevNonCommentSibling
import com.intellij.psi.tree.TokenSet.create as ts

fun createSpacingBuilder(commonSettings: CommonCodeStyleSettings,
                         @Suppress("UNUSED_PARAMETER") rustSettings: RustCodeStyleSettings): SpacingBuilder {

    // Use `sbX` temporaries to work around
    // https://youtrack.jetbrains.com/issue/KT-12239

    val sb1 = SpacingBuilder(commonSettings)
        // Rules defined earlier have higher priority.
        // Beware of comments between blocks!

        //== some special operators
        // FIXME(jajakobyly): Doesn't work well with comments
        .afterInside(COMMA, ts(STRUCT_DECL_ARGS, ENUM_BODY)).spacing(1, 1, 1, true, 1)
        .afterInside(COMMA, ts(ENUM_STRUCT_ARGS, STRUCT_EXPR_BODY)).spacing(1, 1, 0, true, 1)
        .after(COMMA).spacing(1, 1, 0, true, 0)
        .before(COMMA).spaceIf(false)
        .after(COLON).spaceIf(true)
        .before(COLON).spaceIf(false)
        .after(SEMICOLON).spaceIf(true)
        .before(SEMICOLON).spaceIf(false)
        .afterInside(AND, ts(REF_TYPE, SELF_ARGUMENT, PAT_REF, PARAMETER)).spaces(0)
        .beforeInside(Q, TRY_EXPR).spaces(0)
        .afterInside(UNARY_OPS, UNARY_EXPR).spaces(0)

        //== attributes
        .aroundInside(ts(SHA, EXCL, LBRACK, RBRACK), ATTRS).spaces(0)
        .aroundInside(ts(LPAREN, RPAREN), META_ITEM).spaces(0)

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
        .aroundInside(OR, PARAMS_LIKE).spacing(0, 0, 0, false, 0)

    val sb2 = sb1
        //== items
        .between(PARAMS_LIKE, RET_TYPE).spacing(1, 1, 0, true, 0)
        .before(WHERE_CLAUSE).spacing(1, 1, 0, true, 0)
        .applyForEach(BLOCK_LIKE) { before(it).spaces(1) }
        .beforeInside(LBRACE, FLAT_BRACE_BLOCKS).spaces(1)

        .between(ts(IDENTIFIER, FN), PARAMS_LIKE).spaceIf(false)
        .between(IDENTIFIER, GENERIC_PARAMS).spaceIf(false)
        .between(IDENTIFIER, GENERIC_ARGS).spaceIf(false)
        .between(IDENTIFIER, ARG_LIST).spaceIf(false)
        .between(GENERIC_PARAMS, PARAMS_LIKE).spaceIf(false)
        .beforeInside(ARG_LIST, CALL_EXPR).spaceIf(false)

        .between(BINDING_MODE, IDENTIFIER).spaces(1)
        .between(IMPL, GENERIC_PARAMS).spaces(0)
        .afterInside(GENERIC_PARAMS, IMPL_ITEM).spaces(1)
        .betweenInside(ts(GENERIC_PARAMS), TYPES, IMPL_ITEM).spaces(1)

        // Handling blocks is pretty complicated. Do not tamper with
        // them too much and let rustfmt do all the pesky work.
        // Some basic transformation from in-line block to multi-line block
        // is also performed; see doc of #blockMustBeMultiLine() for details.
        .afterInside(LBRACE, BLOCK_LIKE).spacing(1, 1, 0, true, 0)
        .beforeInside(RBRACE, BLOCK_LIKE).spacing(1, 1, 0, true, 0)
        .withinPairInside(LBRACE, RBRACE, PAT_STRUCT).spacing(1, 1, 0, true, 0)

        .betweenInside(IDENTIFIER, ALIAS, EXTERN_CRATE_ITEM).spaces(1)

        .betweenInside(IDENTIFIER, ENUM_TUPLE_ARGS, ENUM_VARIANT).spaces(0)
        .betweenInside(IDENTIFIER, ENUM_DISCRIMINANT, ENUM_VARIANT).spaces(1)

    return sb2
        //== types
        .afterInside(LIFETIME, REF_TYPE).spaceIf(true)
        .betweenInside(ts(MUL), ts(CONST, MUT), PTR_TYPE).spaces(0)
        .before(TYPE_PARAM_BOUNDS).spaces(0)
        .beforeInside(LPAREN, PATH).spaces(0)

        //== expressions
        .beforeInside(LPAREN, PAT_ENUM).spaces(0)
        .beforeInside(LBRACK, INDEX_EXPR).spaces(0)
        .afterInside(PARAMS_LIKE, LAMBDA_EXPR).spacing(1, 1, 0, true, 1)
        .between(MATCH_ARM, MATCH_ARM).spacing(0, 0, 1, true, 1)

        //== macros
        .betweenInside(IDENTIFIER, EXCL, MACRO_INVOCATION).spaces(0)
        .between(MACRO_INVOCATION, MACRO_ARGS).spaces(0)
        .betweenInside(MACRO_INVOCATION, IDENTIFIER, MACRO_DEFINITION).spaces(1)
        .betweenInside(IDENTIFIER, MACRO_ARG, MACRO_DEFINITION).spaces(1)

        //== rules with very large area of application
        .around(NO_SPACE_AROUND_OPS).spaces(0)
        .around(SPACE_AROUND_OPS).spaces(1)
        .around(KEYWORDS).spaces(1)
}

fun Block.computeSpacing(child1: Block?, child2: Block, ctx: RustFmtContext): Spacing? {
    if (child1 is ASTBlock && child2 is ASTBlock) SpacingContext.create(child1, child2).apply {
        when {
            // #[attr]\n<comment>\n => #[attr] <comment>\n etc.
            psi1 is RustOuterAttrElement && psi2 is PsiComment
            -> return one()

            // Ensure that each attribute is in separate line; comment aware
            psi1 is RustOuterAttrElement && (psi2 is RustOuterAttrElement || psi1.parent is RustItemElement)
                || psi1 is PsiComment && (psi2 is RustOuterAttrElement || psi1.getPrevNonCommentSibling() is RustOuterAttrElement)
            -> return lineBreak(keepBlankLines = 0)

            // { ... } => {\n ...\n }, see blockMustBeMultiLine docs for details
            blockMustBeMultiLine()
            -> return lineBreak(keepBlankLines = 0)

            // Format blank lines between statements (or return expression)
            ncPsi1 is RustStmtElement && ncPsi2.isStmtOrExpr
            -> return lineBreak(
                keepLineBreaks = ctx.commonSettings.KEEP_LINE_BREAKS,
                keepBlankLines = ctx.commonSettings.KEEP_BLANK_LINES_IN_CODE)

            // Format blank lines between impl & trait members
            parentPsi is RustImplBodyElement || parentPsi is RustTraitBodyElement
                && !ncPsi1.isBlockDelim && !ncPsi2.isBlockDelim
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
                                  val parentPsi: PsiElement?,
                                  val ncPsi1: PsiElement,
                                  val ncPsi2: PsiElement) {
    companion object {
        fun create(child1: ASTBlock, child2: ASTBlock): SpacingContext {
            val node1 = child1.node
            val node2 = child2.node
            val psi1 = node1.psi
            val psi2 = node2.psi
            val elementType1 = psi1.node.elementType
            val elementType2 = psi2.node.elementType
            val parentPsi = psi1.parent
            val (ncPsi1, ncPsi2) = omitCommentBlocks(node1, psi1, node2, psi2)
            return SpacingContext(node1, node2, psi1, psi2, elementType1, elementType2, parentPsi, ncPsi1, ncPsi2)
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

private fun one(): Spacing = createSpacing(1, 1, 0, false, 0)

private fun lineBreak(minLineFeeds: Int = 1,
                      keepLineBreaks: Boolean = true,
                      keepBlankLines: Int = 1): Spacing =
    createSpacing(0, Int.MAX_VALUE, minLineFeeds, keepLineBreaks, keepBlankLines)

private fun ASTNode.hasLineBreakAfterInSameParent(): Boolean =
    treeNext != null && TreeUtil.findFirstLeaf(treeNext).isWhiteSpaceWithLineBreak()

private fun ASTNode.hasLineBreakBeforeInSameParent(): Boolean =
    treePrev != null && TreeUtil.findLastLeaf(treePrev).isWhiteSpaceWithLineBreak()

private fun ASTNode?.isWhiteSpaceWithLineBreak(): Boolean =
    this != null && elementType == WHITE_SPACE && containsEOL()

/**
 * Check whether blocks must be laid out multi-line:
 *  1. one brace is placed as it's a single-line block while the other - multi-line
 *  2. there are 2 or more statements/expressions inside if it's a code block
 *  3. it's item's body block
 */
private fun SpacingContext.blockMustBeMultiLine(): Boolean {
    if (elementType1 != LBRACE && elementType2 != RBRACE) return false

    val lbrace = (if (elementType1 == LBRACE) node1 else TreeUtil.findSiblingBackward(node2, LBRACE)) ?: return false
    val rbrace = (if (elementType2 == RBRACE) node2 else TreeUtil.findSibling(node1, RBRACE)) ?: return false

    val lbraceIsNewline = lbrace.hasWhitespaceAfterIgnoringComments()
    val rbraceIsNewline = rbrace.hasWhitespaceBeforeIgnoringComments()
    if (lbraceIsNewline xor rbraceIsNewline) {
        return true // 1
    }

    val childrenCount = countNonWhitespaceASTNodesBetween(lbrace, rbrace)

    return when (parentPsi) {
        is RustBlockElement -> childrenCount != 0 && (childrenCount >= 2 || parentPsi.parent is RustItemElement) // 2

        is RustStructDeclArgsElement,
        is RustEnumBodyElement,
        is RustTraitBodyElement,
        is RustModItemElement,
        is RustForeignModItemElement -> childrenCount != 0 // 3

        else -> false
    }
}

private fun SpacingContext.needsBlankLineBetweenItems(): Boolean {
    // Allow to keep consecutive runs of `use`, `const` or `static` items without blank lines
    if (elementType1 in COMMENTS_TOKEN_SET || elementType2 in COMMENTS_TOKEN_SET)
        return false

    return !(elementType1 == elementType2 && elementType1 in ONE_LINE_ITEMS)
}

private fun countNonWhitespaceASTNodesBetween(left: ASTNode, right: ASTNode): Int {
    require(left.treeParent == right.treeParent && left.startOffset < right.startOffset)
    var count = 0
    var next: ASTNode? = left
    while (next != null && next != right) {
        next = FormatterUtil.getNext(next, WHITE_SPACE)
        count += 1
    }
    return count - 1 // subtract right node
}

private fun ASTNode.hasWhitespaceAfterIgnoringComments(): Boolean {
    val lastWS = psi.getNextNonCommentSibling()?.prevSibling
    return lastWS is PsiWhiteSpace && lastWS.containsEOL()
}

private fun ASTNode.hasWhitespaceBeforeIgnoringComments(): Boolean {
    val lastWS = psi.getPrevNonCommentSibling()?.nextSibling
    return lastWS is PsiWhiteSpace && lastWS.containsEOL()
}
