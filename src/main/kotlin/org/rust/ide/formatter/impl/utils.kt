package org.rust.ide.formatter.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.TokenSet.orSet
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RustCompositeElementTypes.*
import org.rust.lang.core.psi.RustTokenElementTypes.*
import com.intellij.psi.tree.TokenSet.create as ts

val KEYWORDS = ts(*IElementType.enumerate { it is RustKeywordTokenType })

val NO_SPACE_AROUND_OPS = ts(COLONCOLON, DOT, DOTDOT)
val SPACE_AROUND_OPS = TokenSet.andNot(ALL_OPS, NO_SPACE_AROUND_OPS)
val UNARY_OPS = ts(MINUS, MUL, EXCL, AND, ANDAND)

val PARAMS_LIKE = ts(PARAMETERS, VARIADIC_PARAMETERS)
val PAREN_DELIMITED_BLOCKS = orSet(PARAMS_LIKE,
    ts(PAREN_EXPR, TUPLE_EXPR, TUPLE_TYPE, ARG_LIST, FORMAT_MACRO_ARGS, TRY_MACRO_ARGS, PAT_TUP, TUPLE_FIELDS))
val PAREN_LISTS = orSet(PAREN_DELIMITED_BLOCKS, ts(PAT_ENUM))

val BRACK_DELIMITED_BLOCKS = ts(VEC_TYPE, ARRAY_EXPR, FORMAT_MACRO_ARGS, TRY_MACRO_ARGS)
val BRACK_LISTS = orSet(BRACK_DELIMITED_BLOCKS, ts(INDEX_EXPR))

val BLOCK_LIKE = ts(BLOCK, BLOCK_FIELDS, STRUCT_EXPR_BODY, MATCH_BODY, ENUM_BODY)
val BRACE_LISTS = ts(USE_GLOB_LIST, FORMAT_MACRO_ARGS, TRY_MACRO_ARGS)
val BRACE_DELIMITED_BLOCKS = orSet(BLOCK_LIKE, BRACE_LISTS)

val ANGLE_DELIMITED_BLOCKS = ts(GENERIC_PARAMS, GENERIC_ARGS, FOR_LIFETIMES)
val ANGLE_LISTS = orSet(ANGLE_DELIMITED_BLOCKS, ts(QUAL_PATH_EXPR))

val ATTRS = ts(OUTER_ATTR, INNER_ATTR)
val MOD_ITEMS = ts(FOREIGN_MOD_ITEM, MOD_ITEM)

val DELIMITED_BLOCKS = orSet(BRACE_DELIMITED_BLOCKS, BRACK_DELIMITED_BLOCKS,
    PAREN_DELIMITED_BLOCKS, ANGLE_DELIMITED_BLOCKS)
val FLAT_BRACE_BLOCKS = orSet(MOD_ITEMS, ts(PAT_STRUCT, TRAIT_ITEM, IMPL_ITEM))

val TYPES = ts(VEC_TYPE, PTR_TYPE, REF_TYPE, BARE_FN_TYPE, TUPLE_TYPE, PATH_TYPE,
    TYPE_WITH_BOUNDS_TYPE, FOR_IN_TYPE, WILDCARD_TYPE)

val MACRO_ARGS = ts(MACRO_ARG, FORMAT_MACRO_ARGS, TRY_MACRO_ARGS)

val FN_DECLS = ts(FN_ITEM, FOREIGN_FN_DECL, TRAIT_METHOD_MEMBER, IMPL_METHOD_MEMBER, BARE_FN_TYPE, LAMBDA_EXPR)

val ONE_LINE_ITEMS = ts(USE_ITEM, CONST_ITEM, STATIC_ITEM, MOD_DECL_ITEM, EXTERN_CRATE_ITEM, TYPE_ITEM, INNER_ATTR)

val PsiElement.isTopLevelItem: Boolean
    get() = (this is RustItemElement || this is RustAttrElement) && parent is RustMod

val PsiElement.isStmtOrExpr: Boolean
    get() = this is RustStmtElement || this is RustExprElement

val PsiElement.isBlockDelim: Boolean
    get() = node.isBlockDelim

fun onSameLine(e1: PsiElement, e2: PsiElement): Boolean {
    val documentManager = PsiDocumentManager.getInstance(e1.project)
    val document = documentManager.getDocument(e1.containingFile)
    return if (document != null && document == documentManager.getDocument(e2.containingFile)) {
        document.getLineNumber(e1.textOffset) == document.getLineNumber(e2.textOffset)
    } else {
        false
    }
}


val ASTNode.isDelimitedBlock: Boolean
    get() = elementType in DELIMITED_BLOCKS

val ASTNode.isFlatBraceBlock: Boolean
    get() = elementType in FLAT_BRACE_BLOCKS

/**
 * A flat block is a Rust PSI element which does not denote separate PSI
 * element for its _block_ part (e.g. `{...}`), for example [MOD_ITEM].
 */
val ASTNode.isFlatBlock: Boolean
    get() = isFlatBraceBlock || elementType == PAT_ENUM

val ASTNode.isBlockDelim: Boolean
    get() = isBlockDelim(treeParent)

fun ASTNode.isBlockDelim(parent: ASTNode?): Boolean {
    if (parent == null) return false
    val parentType = parent.elementType
    return when (elementType) {
        LBRACE, RBRACE -> parentType in BRACE_DELIMITED_BLOCKS || parent.isFlatBraceBlock
        LBRACK, RBRACK -> parentType in BRACK_LISTS
        LPAREN, RPAREN -> parentType in PAREN_LISTS || parentType == PAT_ENUM
        LT, GT -> parentType in ANGLE_LISTS
        OR -> parentType == PARAMETERS && parent.treeParent?.elementType == LAMBDA_EXPR
        else -> false
    }
}

fun ASTNode?.isWhitespaceOrEmpty() = this == null || textLength == 0 || elementType == WHITE_SPACE

fun onSameLine(e1: ASTNode, e2: ASTNode): Boolean = onSameLine(e1.psi, e2.psi)

fun ASTNode.treeNonWSPrev(): ASTNode? {
    var current = this.treePrev
    while (current?.elementType == WHITE_SPACE) {
        current = current?.treePrev
    }
    return current
}

fun ASTNode.treeNonWSNext(): ASTNode? {
    var current = this.treeNext
    while (current?.elementType == WHITE_SPACE) {
        current = current?.treeNext
    }
    return current
}
