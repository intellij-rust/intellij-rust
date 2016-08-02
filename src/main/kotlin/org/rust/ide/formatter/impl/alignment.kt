package org.rust.ide.formatter.impl

import org.rust.ide.formatter.RustAlignmentStrategy
import org.rust.ide.formatter.blocks.RustFmtBlock
import org.rust.lang.core.psi.RustCompositeElementTypes.*
import org.rust.lang.core.psi.RustTokenElementTypes.DOT

fun RustFmtBlock.getAlignmentStrategy(): RustAlignmentStrategy = when (node.elementType) {
    TUPLE_EXPR, ARG_LIST, FORMAT_MACRO_ARGS, TRY_MACRO_ARGS ->
        RustAlignmentStrategy.wrap()
            .alignIf { child, parent, ctx ->
                // Do not align if we have only one argument as this may lead to
                // some quirks when that argument is tuple expr.
                // Alignment do not allow "negative indentation" i.e.:
                //     func((
                //         happy tuple param
                //     ))
                // would be formatted to:
                //     func((
                //              happy tuple param
                //          ))
                // because due to applied alignment, closing paren has to be
                // at least in the same column as the opening one.
                var result = true
                if (parent != null) {
                    val lBrace = parent.firstChildNode
                    val rBrace = parent.lastChildNode
                    if (lBrace.isBlockDelim(parent) && rBrace.isBlockDelim(parent)) {
                        result = child.treeNonWSPrev() != lBrace || child.treeNonWSNext() != rBrace
                    }
                }
                result
            }
            .alignUnlessBlockDelim()
            .alignIf(ctx.commonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS)

    TUPLE_TYPE, TUPLE_FIELDS ->
        RustAlignmentStrategy.wrap()
            .alignUnlessBlockDelim()
            .alignIf(ctx.commonSettings.ALIGN_MULTILINE_PARAMETERS)

    in PARAMS_LIKE ->
        RustAlignmentStrategy.shared()
            .alignUnlessBlockDelim()
            .alignIf(ctx.commonSettings.ALIGN_MULTILINE_PARAMETERS)

    in FN_DECLS ->
        RustAlignmentStrategy.shared()
            .alignIf(RET_TYPE, WHERE_CLAUSE)
            .alignIf(ctx.rustSettings.ALIGN_RET_TYPE_AND_WHERE_CLAUSE)

    PAT_ENUM ->
        RustAlignmentStrategy.wrap()
            .alignIf { c, p, x -> x.metLBrace && !c.isBlockDelim(p) }
            .alignIf(ctx.commonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS)

    METHOD_CALL_EXPR ->
        RustAlignmentStrategy.shared()
            .alignIf(DOT) // DOT is synthetic's block representative
            .alignIf(ctx.commonSettings.ALIGN_MULTILINE_CHAINED_METHODS)

    WHERE_CLAUSE ->
        RustAlignmentStrategy.wrap()
            .alignIf(WHERE_PRED)
            .alignIf(ctx.rustSettings.ALIGN_WHERE_BOUNDS)

    GENERIC_PARAMS ->
        RustAlignmentStrategy.wrap()
            .alignIf(TYPE_PARAM, LIFETIME_PARAM)
            .alignIf(ctx.rustSettings.ALIGN_TYPE_PARAMS)

    FOR_LIFETIMES ->
        RustAlignmentStrategy.wrap()
            .alignIf(LIFETIME_PARAM)
            .alignIf(ctx.rustSettings.ALIGN_TYPE_PARAMS)

    else -> RustAlignmentStrategy.NullStrategy
}

fun RustAlignmentStrategy.alignUnlessBlockDelim(): RustAlignmentStrategy = alignIf { c, p, x -> !c.isBlockDelim(p) }
