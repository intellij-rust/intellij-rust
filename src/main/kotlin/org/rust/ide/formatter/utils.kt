package org.rust.ide.formatter

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
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

// PATH_PART because `Fn(A) -> R`
internal val PAREN_LIST_HOLDERS = ts(PAREN_EXPR, TUPLE_EXPR, TUPLE_TYPE, PARAMETERS,
    VARIADIC_PARAMETERS, ARG_LIST, IMPL_METHOD_MEMBER, BARE_FN_TYPE, PATH_PART,
    PAT_ENUM, PAT_TUP, ENUM_TUPLE_ARGS)
internal val BRACK_LIST_HOLDERS = ts(VEC_TYPE, ARRAY_EXPR, INDEX_EXPR)
internal val BRACE_LIST_HOLDERS = ts(USE_GLOB_LIST)
internal val ANGLE_LIST_HOLDERS = ts(GENERIC_PARAMS, GENERIC_ARGS, QUAL_PATH_EXPR)
internal val PARAMS_LIKE = ts(PARAMETERS, VARIADIC_PARAMETERS)

internal val ATTRS = ts(OUTER_ATTR, INNER_ATTR)

internal val BLOCK_LIKE = ts(BLOCK, STRUCT_DECL_ARGS, STRUCT_EXPR_BODY, IMPL_BODY, MATCH_BODY,
    TRAIT_BODY, ENUM_BODY, ENUM_STRUCT_ARGS)

internal val TYPES = ts(VEC_TYPE, PTR_TYPE, REF_TYPE, BARE_FN_TYPE, TUPLE_TYPE, PATH_TYPE,
    TYPE_WITH_BOUNDS_TYPE, FOR_IN_TYPE, WILDCARD_TYPE)

internal val MACRO_ARGS = ts(MACRO_ARG, FORMAT_MACRO_ARGS, TRY_MACRO_ARGS)

internal val PsiElement.isTopLevelItem: Boolean
    get() = (this is RustItem || this is RustAttr) && this.parent is RustFile

internal val PsiElement.isStmtOrExpr: Boolean
    get() = this is RustStmt || this is RustExpr
