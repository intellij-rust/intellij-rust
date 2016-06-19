package org.rust.ide.formatter.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet.orSet
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RustCompositeElementTypes.*
import org.rust.lang.core.psi.RustTokenElementTypes.*
import com.intellij.psi.tree.TokenSet.create as ts

val KEYWORDS = ts(*IElementType.enumerate { it is RustKeywordTokenType })

val NO_SPACE_AROUND_OPS = ts(COLONCOLON, DOT, DOTDOT)
val SPACE_AROUND_OPS = ts(AND, ANDAND, ANDEQ, ARROW, FAT_ARROW, DIV, DIVEQ, EQ, EQEQ, EXCLEQ, GT, LT, MINUSEQ, MUL,
    MULEQ, OR, OREQ, OROR, PLUSEQ, REM, REMEQ, XOR, XOREQ, MINUS, PLUS, GTGTEQ, GTGT, GTEQ, LTLTEQ, LTLT, LTEQ)
val UNARY_OPS = ts(MINUS, MUL, EXCL, AND, ANDAND)

val PARAMS_LIKE = ts(PARAMETERS, VARIADIC_PARAMETERS)
val PAREN_DELIMITED_BLOCKS = orSet(PARAMS_LIKE,
    ts(PAREN_EXPR, TUPLE_EXPR, TUPLE_TYPE, ARG_LIST, FORMAT_MACRO_ARGS, TRY_MACRO_ARGS, PAT_TUP, ENUM_TUPLE_ARGS))
val PAREN_LISTS = orSet(PAREN_DELIMITED_BLOCKS, ts(PAT_ENUM))

val BRACK_DELIMITED_BLOCKS = ts(VEC_TYPE, ARRAY_EXPR, FORMAT_MACRO_ARGS, TRY_MACRO_ARGS)
val BRACK_LISTS = orSet(BRACK_DELIMITED_BLOCKS, ts(INDEX_EXPR))

val BLOCK_LIKE = ts(BLOCK, STRUCT_DECL_ARGS, STRUCT_EXPR_BODY, IMPL_BODY, MATCH_BODY,
    TRAIT_BODY, ENUM_BODY, ENUM_STRUCT_ARGS)
val BRACE_LISTS = ts(USE_GLOB_LIST, FORMAT_MACRO_ARGS, TRY_MACRO_ARGS)
val BRACE_DELIMITED_BLOCKS = orSet(BLOCK_LIKE, BRACE_LISTS)

val ANGLE_DELIMITED_BLOCKS = ts(GENERIC_PARAMS, GENERIC_ARGS)
val ANGLE_LISTS = orSet(ANGLE_DELIMITED_BLOCKS, ts(QUAL_PATH_EXPR))

val ATTRS = ts(OUTER_ATTR, INNER_ATTR)
val MOD_ITEMS = ts(FOREIGN_MOD_ITEM, MOD_ITEM)

val DELIMITED_BLOCKS = orSet(BRACE_DELIMITED_BLOCKS, BRACK_DELIMITED_BLOCKS,
    PAREN_DELIMITED_BLOCKS, ANGLE_DELIMITED_BLOCKS)
val FLAT_BRACE_BLOCKS = orSet(MOD_ITEMS, ts(PAT_STRUCT))

val TYPES = ts(VEC_TYPE, PTR_TYPE, REF_TYPE, BARE_FN_TYPE, TUPLE_TYPE, PATH_TYPE,
    TYPE_WITH_BOUNDS_TYPE, FOR_IN_TYPE, WILDCARD_TYPE)

val MACRO_ARGS = ts(MACRO_ARG, FORMAT_MACRO_ARGS, TRY_MACRO_ARGS)

val FN_DECLS = ts(FN_ITEM, FOREIGN_FN_DECL, TRAIT_METHOD_MEMBER, IMPL_METHOD_MEMBER, BARE_FN_TYPE, LAMBDA_EXPR)

val FN_SHARED_ALIGN_OWNERS = orSet(PARAMS_LIKE, ts(RET_TYPE, WHERE_CLAUSE))


val PsiElement.isTopLevelItem: Boolean
    get() = (this is RustItemElement || this is RustAttrElement) && parent is RustMod

val PsiElement.isStmtOrExpr: Boolean
    get() = this is RustStmtElement || this is RustExprElement

val PsiElement.isBlockDelim: Boolean
    get() = node.isBlockDelim


val ASTNode.isDelimitedBlock: Boolean
    get() = DELIMITED_BLOCKS.contains(elementType)

val ASTNode.isModItem: Boolean
    get() = MOD_ITEMS.contains(elementType)

val ASTNode.isFlatBraceBlock: Boolean
    get() = FLAT_BRACE_BLOCKS.contains(elementType)

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
        LBRACE, RBRACE -> BRACE_DELIMITED_BLOCKS.contains(parentType) || parent.isFlatBraceBlock
        LBRACK, RBRACK -> BRACK_LISTS.contains(parentType)
        LPAREN, RPAREN -> PAREN_LISTS.contains(parentType) || parentType == PAT_ENUM
        LT, GT -> ANGLE_LISTS.contains(parentType)
        OR -> parentType == PARAMETERS && parent.treeParent?.elementType == LAMBDA_EXPR
        else -> false
    }
}

fun ASTNode?.isWhitespaceOrEmpty() = this == null || textLength == 0 || elementType == WHITE_SPACE
