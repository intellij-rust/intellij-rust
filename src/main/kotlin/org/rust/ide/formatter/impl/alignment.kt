package org.rust.ide.formatter.impl

import org.rust.ide.formatter.RustAlignmentStrategy
import org.rust.ide.formatter.blocks.RustFmtBlock
import org.rust.lang.core.psi.RustCompositeElementTypes.*
import org.rust.lang.core.psi.RustTokenElementTypes.DOT

fun RustFmtBlock.getAlignmentStrategy(): RustAlignmentStrategy = when (node.elementType) {
    TUPLE_EXPR, ARG_LIST, FORMAT_MACRO_ARGS, TRY_MACRO_ARGS ->
        RustAlignmentStrategy.wrap()
            .alignUnlessBlockDelim()
            .alignIf(ctx.commonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS)

    TUPLE_TYPE, ENUM_TUPLE_ARGS ->
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
            .alignIf(TYPE_PARAM)
            .alignIf(ctx.rustSettings.ALIGN_TYPE_PARAMS)

    else -> RustAlignmentStrategy.NullStrategy
}

fun RustAlignmentStrategy.alignUnlessBlockDelim(): RustAlignmentStrategy = alignIf { c, p, x -> !c.isBlockDelim(p) }
