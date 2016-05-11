package org.rust.ide.formatter

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet.orSet
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RustCompositeElementTypes.*
import org.rust.lang.core.psi.RustTokenElementTypes.*
import org.rust.lang.core.psi.impl.RustFile
import com.intellij.psi.tree.TokenSet.create as ts

internal val KEYWORDS = ts(*IElementType.enumerate { it is RustKeywordTokenType })

internal val NO_SPACE_AROUND_OPS = ts(COLONCOLON, DOT, DOTDOT)
internal val SPACE_AROUND_OPS = ts(AND, ANDAND, ANDEQ, ARROW, FAT_ARROW, DIV, DIVEQ, EQ, EQEQ,
    EXCLEQ, GT, LT, MINUSEQ, MUL, MULEQ, OR, OREQ, OROR, PLUSEQ, REM, REMEQ, XOR, XOREQ, MINUS,
    PLUS, GTGTEQ, GTGT, GTEQ, LTLTEQ, LTLT, LTEQ)
internal val UNARY_OPS = ts(MINUS, MUL, EXCL, AND, ANDAND)

internal val PARAMS_LIKE = ts(PARAMETERS, VARIADIC_PARAMETERS)
internal val PAREN_DELIMITED_BLOCKS = orSet(PARAMS_LIKE,
    ts(PAREN_EXPR, TUPLE_EXPR, TUPLE_TYPE, ARG_LIST, PAT_TUP, ENUM_TUPLE_ARGS))
internal val PAREN_LISTS = orSet(PAREN_DELIMITED_BLOCKS, ts(PAT_ENUM))

internal val BRACK_DELIMITED_BLOCKS = ts(VEC_TYPE, ARRAY_EXPR)
internal val BRACK_LISTS = orSet(BRACK_DELIMITED_BLOCKS, ts(INDEX_EXPR))

internal val BLOCK_LIKE = ts(BLOCK, STRUCT_DECL_ARGS, STRUCT_EXPR_BODY, IMPL_BODY, MATCH_BODY,
    TRAIT_BODY, ENUM_BODY, ENUM_STRUCT_ARGS)
internal val BRACE_LISTS = ts(USE_GLOB_LIST)
internal val BRACE_DELIMITED_BLOCKS = orSet(BLOCK_LIKE, BRACE_LISTS)

internal val ANGLE_DELIMITED_BLOCKS = ts(GENERIC_PARAMS, GENERIC_ARGS)
internal val ANGLE_LISTS = orSet(ANGLE_DELIMITED_BLOCKS, ts(QUAL_PATH_EXPR))

internal val ATTRS = ts(OUTER_ATTR, INNER_ATTR)

internal val DELIMITED_BLOCKS = orSet(BRACE_DELIMITED_BLOCKS, BRACK_DELIMITED_BLOCKS,
    PAREN_DELIMITED_BLOCKS, ANGLE_DELIMITED_BLOCKS)
internal val FLAT_BLOCKS = ts(FOREIGN_MOD_ITEM, MOD_ITEM)

internal val TYPES = ts(VEC_TYPE, PTR_TYPE, REF_TYPE, BARE_FN_TYPE, TUPLE_TYPE, PATH_TYPE,
    TYPE_WITH_BOUNDS_TYPE, FOR_IN_TYPE, WILDCARD_TYPE)

internal val MACRO_ARGS = ts(MACRO_ARG, FORMAT_MACRO_ARGS, TRY_MACRO_ARGS)


internal val PsiElement.isTopLevelItem: Boolean
    get() = (this is RustItem || this is RustAttr) && this.parent is RustFile

internal val PsiElement.isStmtOrExpr: Boolean
    get() = this is RustStmt || this is RustExpr


internal val ASTNode.isDelimitedBlock: Boolean
    get() = DELIMITED_BLOCKS.contains(elementType)

internal val ASTNode.isFlatBlock: Boolean
    get() = FLAT_BLOCKS.contains(elementType)

internal val ASTNode.isBlockDelim: Boolean
    get() = isBlockDelim(treeParent)

internal fun ASTNode.isBlockDelim(parent: ASTNode?): Boolean {
    if (parent == null) return false
    val parentType = parent.elementType
    return when (elementType) {
        LBRACE, RBRACE -> BRACE_DELIMITED_BLOCKS.contains(parentType) || parent.isFlatBlock
        LBRACK, RBRACK -> BRACK_LISTS.contains(parentType)
        LPAREN, RPAREN -> PAREN_LISTS.contains(parentType)
        LT, GT -> ANGLE_LISTS.contains(parentType)
        OR -> parentType == PARAMETERS && parent.treeParent?.elementType == LAMBDA_EXPR
        else -> false
    }
}
