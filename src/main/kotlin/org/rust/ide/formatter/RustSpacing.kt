package org.rust.ide.formatter

import com.intellij.formatting.SpacingBuilder
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.psi.RustCompositeElementTypes.*
import org.rust.lang.core.psi.RustKeywordTokenType
import org.rust.lang.core.psi.RustTokenElementTypes.*
import com.intellij.psi.tree.TokenSet.create as ts

object RustSpacing {
    fun createSpacingBuilder(commonSettings: CommonCodeStyleSettings,
                             rustSettings: RustCodeStyleSettings): SpacingBuilder =
        SpacingBuilder(commonSettings)
            // rules defined earlier have higher priority
            //== some special operators
            .applyForEach(SPACE_AFTER_OPS) { after(it).spaceIf(true).before(it).spaceIf(false) }
            .afterInside(AND, ts(REF_TYPE, SELF_ARGUMENT)).spaces(0)
            .beforeInside(Q, TRY_EXPR).spaces(0)
            .afterInside(UNARY_OPS, UNARY_EXPR).spaces(0)

            //== attributes
            .aroundInside(ts(SHA, EXCL, LBRACK, RBRACK), ATTRS).spaces(0)
            .aroundInside(ts(LPAREN, RPAREN), META_ITEM).spaces(0)

            //== empty parens
            .between(LPAREN, RPAREN).spaceIf(false)
            .between(LBRACK, RBRACK).spaceIf(false)
            .between(LBRACE, RBRACE).spaceIf(false)
            .betweenInside(OR, OR, LAMBDA_EXPR).spaceIf(false)

            //== paren delimited lists
            // withinPairInside does not accept TokenSet as parent node set :(
            // and we cannot create our own, because RuleCondition stuff is private
            .afterInside(LPAREN, PAREN_LIST_HOLDERS).spaceIf(false)
            .beforeInside(RPAREN, PAREN_LIST_HOLDERS).spaceIf(false)
            .afterInside(LBRACK, BRACK_LIST_HOLDERS).spaceIf(false)
            .beforeInside(RBRACK, BRACK_LIST_HOLDERS).spaceIf(false)
            .afterInside(LBRACE, BRACE_LIST_HOLDERS).spaceIf(false)
            .beforeInside(RBRACE, BRACE_LIST_HOLDERS).spaceIf(false)
            .afterInside(LT, ANGLE_LIST_HOLDERS).spaceIf(false)
            .beforeInside(GT, ANGLE_LIST_HOLDERS).spaceIf(false)
            .aroundInside(OR, PARAMS_LIKE).spaceIf(false)

            //== items
            .between(PARAMS_LIKE, RET_TYPE).spacing(1, 1, 0, true, 0)
            .before(WHERE_CLAUSE).spacing(1, 1, 0, true, 0)
            .applyForEach(BLOCK_LIKE) { before(it).spaces(1) }
            // FIXME(jajakobyly): nonono
            .beforeInside(LBRACE, ts(FOREIGN_MOD_ITEM, MOD_ITEM)).spaces(1)

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

            .afterInside(LBRACE, BLOCK_LIKE).spacing(1, 1, 0, true, 0)
            .beforeInside(RBRACE, BLOCK_LIKE).spacing(1, 1, 0, true, 0)

            .betweenInside(IDENTIFIER, ALIAS, EXTERN_CRATE_ITEM).spaces(1)

            //== types
            .afterInside(LIFETIME, REF_TYPE).spaceIf(true)
            .betweenInside(ts(MUL), ts(CONST, MUT), PTR_TYPE).spaces(0)
            .before(TYPE_PARAM_BOUNDS).spaces(0)
            .beforeInside(LPAREN, PATH_PART).spaces(0)

            //== expressions
            .beforeInside(LPAREN, PAT_ENUM).spaces(0)
            .beforeInside(LBRACK, INDEX_EXPR).spaces(0)
            .afterInside(PARAMS_LIKE, LAMBDA_EXPR).spacing(1, 1, 0, true, 1)

            //== macros
            .betweenInside(IDENTIFIER, EXCL, MACRO_INVOCATION).spaces(0)
            .between(MACRO_INVOCATION, MACRO_ARGS).spaces(0)
            .betweenInside(MACRO_INVOCATION, IDENTIFIER, MACRO_DEFINITION).spaces(1)
            .betweenInside(IDENTIFIER, MACRO_ARG, MACRO_DEFINITION).spaces(1)

            //== rules with very large area of application
            .around(NO_SPACE_AROUND_OPS).spaces(0)
            .around(SPACE_AROUND_OPS).spaces(1)
            .around(KEYWORDS).spaces(1)

    private val KEYWORDS = ts(*IElementType.enumerate { it is RustKeywordTokenType })
    private val NO_SPACE_AROUND_OPS = ts(COLONCOLON, DOT, DOTDOT)
    private val SPACE_AROUND_OPS = ts(AND, ANDAND, ANDEQ, ARROW, FAT_ARROW, DIV, DIVEQ, EQ, EQEQ,
        EXCLEQ, GT, LT, MINUSEQ, MUL, MULEQ, OR, OREQ, OROR, PLUSEQ, REM, REMEQ, XOR, XOREQ, MINUS, PLUS,
        GTGTEQ, GTGT, GTEQ, LTLTEQ, LTLT, LTEQ)
    private val UNARY_OPS = ts(MINUS, MUL, EXCL, AND, ANDAND)
    private val SPACE_AFTER_OPS = ts(COMMA, COLON, SEMICOLON)
    // PATH_PART because `Fn(A) -> R`
    private val PAREN_LIST_HOLDERS = ts(PAREN_EXPR, TUPLE_EXPR, TUPLE_TYPE, PARAMETERS, VARIADIC_PARAMETERS, ARG_LIST,
        IMPL_METHOD_MEMBER, BARE_FN_TYPE, PATH_PART, PAT_ENUM, PAT_TUP)
    private val BRACK_LIST_HOLDERS = ts(VEC_TYPE, ARRAY_EXPR, INDEX_EXPR)
    private val BRACE_LIST_HOLDERS = ts(USE_GLOB_LIST)
    private val ANGLE_LIST_HOLDERS = ts(GENERIC_PARAMS, GENERIC_ARGS, QUAL_PATH_EXPR)
    private val ATTRS = ts(OUTER_ATTR, INNER_ATTR)
    private val BLOCK_LIKE = ts(BLOCK, STRUCT_DECL_ARGS, STRUCT_EXPR_BODY, IMPL_BODY, MATCH_BODY, TRAIT_BODY)
    private val TYPES = ts(VEC_TYPE, PTR_TYPE, REF_TYPE, BARE_FN_TYPE, TUPLE_TYPE, PATH_TYPE,
        TYPE_WITH_BOUNDS_TYPE, FOR_IN_TYPE, WILDCARD_TYPE)
    private val MACRO_ARGS = ts(MACRO_ARG, FORMAT_MACRO_ARGS, TRY_MACRO_ARGS)
    private val PARAMS_LIKE = ts(PARAMETERS, VARIADIC_PARAMETERS)

    inline fun SpacingBuilder.applyForEach(tokenSet: TokenSet,
                                           block: SpacingBuilder.(IElementType) -> SpacingBuilder): SpacingBuilder {
        var self = this
        for (tt in tokenSet.types) {
            self = block(this, tt)
        }
        return self
    }
}
