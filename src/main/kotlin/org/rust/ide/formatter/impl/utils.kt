/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.TokenSet.orSet
import org.rust.lang.core.psi.RS_OPERATORS
import org.rust.lang.core.psi.RsAttr
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsStmt
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.elementType
import com.intellij.psi.tree.TokenSet.create as ts

val SPECIAL_MACRO_ARGS = ts(FORMAT_MACRO_ARGUMENT, LOG_MACRO_ARGUMENT, TRY_MACRO_ARGUMENT, VEC_MACRO_ARGUMENT, ASSERT_MACRO_ARGUMENT)

val NO_SPACE_AROUND_OPS = ts(COLONCOLON, DOT, DOTDOT)
val SPACE_AROUND_OPS = TokenSet.andNot(RS_OPERATORS, NO_SPACE_AROUND_OPS)
val UNARY_OPS = ts(MINUS, MUL, EXCL, AND, ANDAND)

val PAREN_DELIMITED_BLOCKS = orSet(ts(VALUE_PARAMETER_LIST, PAREN_EXPR, TUPLE_EXPR, TUPLE_TYPE, VALUE_ARGUMENT_LIST, PAT_TUP, TUPLE_FIELDS, VIS_RESTRICTION),
    SPECIAL_MACRO_ARGS)
val PAREN_LISTS = orSet(PAREN_DELIMITED_BLOCKS, ts(PAT_ENUM))

val BRACK_DELIMITED_BLOCKS = orSet(ts(ARRAY_TYPE, ARRAY_EXPR), SPECIAL_MACRO_ARGS)
val BRACK_LISTS = orSet(BRACK_DELIMITED_BLOCKS, ts(INDEX_EXPR))

val BLOCK_LIKE = ts(BLOCK, BLOCK_FIELDS, STRUCT_LITERAL_BODY, MATCH_BODY, ENUM_BODY, MEMBERS)
val BRACE_LISTS = orSet(ts(USE_GROUP), SPECIAL_MACRO_ARGS)
val BRACE_DELIMITED_BLOCKS = orSet(BLOCK_LIKE, BRACE_LISTS)

val ANGLE_DELIMITED_BLOCKS = ts(TYPE_PARAMETER_LIST, TYPE_ARGUMENT_LIST, FOR_LIFETIMES)
val ANGLE_LISTS = orSet(ANGLE_DELIMITED_BLOCKS, ts(TYPE_QUAL))

val ATTRS = ts(OUTER_ATTR, INNER_ATTR)
val MOD_ITEMS = ts(FOREIGN_MOD_ITEM, MOD_ITEM)

val DELIMITED_BLOCKS = orSet(BRACE_DELIMITED_BLOCKS, BRACK_DELIMITED_BLOCKS,
    PAREN_DELIMITED_BLOCKS, ANGLE_DELIMITED_BLOCKS)
val FLAT_BRACE_BLOCKS = orSet(MOD_ITEMS, ts(PAT_STRUCT))

val TYPES = ts(ARRAY_TYPE, REF_LIKE_TYPE, FN_POINTER_TYPE, TUPLE_TYPE, BASE_TYPE, FOR_IN_TYPE)

val FN_DECLS = ts(FUNCTION, FN_POINTER_TYPE, LAMBDA_EXPR)

val ONE_LINE_ITEMS = ts(USE_ITEM, CONSTANT, MOD_DECL_ITEM, EXTERN_CRATE_ITEM, TYPE_ALIAS, INNER_ATTR)

val PsiElement.isTopLevelItem: Boolean
    get() = (this is RsItemElement || this is RsAttr) && parent is RsMod

val PsiElement.isStmtOrExpr: Boolean
    get() = this is RsStmt || this is RsExpr


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

fun ASTNode.isBlockDelim(parent: ASTNode?): Boolean {
    if (parent == null) return false
    val parentType = parent.elementType
    return when (elementType) {
        LBRACE, RBRACE -> parentType in BRACE_DELIMITED_BLOCKS || parent.isFlatBraceBlock
        LBRACK, RBRACK -> parentType in BRACK_LISTS
        LPAREN, RPAREN -> parentType in PAREN_LISTS || parentType == PAT_ENUM
        LT, GT -> parentType in ANGLE_LISTS
        OR -> parentType == VALUE_PARAMETER_LIST && parent.treeParent?.elementType == LAMBDA_EXPR
        else -> false
    }
}

fun ASTNode?.isWhitespaceOrEmpty() = this == null || textLength == 0 || elementType == WHITE_SPACE

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

class CommaList(
    val list: IElementType,
    val openingBrace: IElementType,
    val closingBrace: IElementType,
    val isElement: (PsiElement) -> Boolean
) {
    val needsSpaceBeforeClosingBrace: Boolean get() = closingBrace == RBRACE && list != USE_GROUP

    override fun toString(): String = "CommaList($list)"

    companion object {
        fun forElement(elementType: IElementType): CommaList? {
            return ALL.find { it.list == elementType }
        }

        private val ALL = listOf(
            CommaList(BLOCK_FIELDS, LBRACE, RBRACE, { it.elementType == FIELD_DECL }),
            CommaList(STRUCT_LITERAL_BODY, LBRACE, RBRACE, { it.elementType == STRUCT_LITERAL_FIELD }),
            CommaList(ENUM_BODY, LBRACE, RBRACE, { it.elementType == ENUM_VARIANT }),
            CommaList(USE_GROUP, LBRACE, RBRACE, { it.elementType == USE_SPECK }),

            CommaList(TUPLE_FIELDS, LPAREN, RPAREN, { it.elementType == TUPLE_FIELD_DECL }),
            CommaList(VALUE_PARAMETER_LIST, LPAREN, RPAREN, { it.elementType == VALUE_PARAMETER }),
            CommaList(VALUE_ARGUMENT_LIST, LPAREN, RPAREN, { it is RsExpr })
        )
    }
}

