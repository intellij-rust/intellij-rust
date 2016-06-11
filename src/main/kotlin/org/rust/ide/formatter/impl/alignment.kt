package org.rust.ide.formatter.impl

import com.intellij.psi.tree.TokenSet
import org.rust.ide.formatter.RustAlignmentStrategy
import org.rust.ide.formatter.blocks.RustFmtBlock
import org.rust.lang.core.psi.RustCompositeElementTypes.*

fun RustFmtBlock.getAlignmentStrategy(): RustAlignmentStrategy = when (node.elementType) {
    TUPLE_EXPR, TUPLE_TYPE, ARG_LIST, ENUM_TUPLE_ARGS ->
        RustAlignmentStrategy.wrapCond { c, p, x -> !c.isBlockDelim(p) }

    in PARAMS_LIKE ->
        RustAlignmentStrategy.lazyWrapCond(
            { it.sharedAlignment },
            { c, p, x -> !c.isBlockDelim(p) })

    in FN_DECLS ->
        RustAlignmentStrategy.lazyWrapFiltered(
            { it.sharedAlignment },
            TokenSet.create(RET_TYPE, WHERE_CLAUSE))

    PAT_ENUM ->
        RustAlignmentStrategy.wrapCond { c, p, x -> x.metLBrace && !c.isBlockDelim(p) }

    else -> RustAlignmentStrategy.NULL_STRATEGY
}
