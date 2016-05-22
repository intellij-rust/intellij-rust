package org.rust.ide.formatter.impl

import com.intellij.formatting.Alignment
import com.intellij.openapi.util.Key
import com.intellij.psi.tree.TokenSet
import org.rust.ide.formatter.RustAlignmentStrategy
import org.rust.ide.formatter.blocks.RustFmtBlock
import org.rust.lang.core.psi.RustCompositeElementTypes.*

/**
 * Stores common alignment object for function declarations's parameters, return type & where clause.
 */
val PARAMETERS_ALIGNMENT: Key<Alignment> = Key.create("PARAMETERS_ALIGNMENT")

fun RustFmtBlock.getAlignmentStrategy(): RustAlignmentStrategy = when (node.elementType) {
    TUPLE_EXPR, TUPLE_TYPE, ARG_LIST, ENUM_TUPLE_ARGS ->
        RustAlignmentStrategy.wrapCond { c, p -> !c.isBlockDelim(p) }

    in PARAMS_LIKE ->
        RustAlignmentStrategy.lazyWrapCond(
            { this.getUserData(PARAMETERS_ALIGNMENT) },
            { c, p -> !c.isBlockDelim(p) })

    in FN_DECLS ->
        RustAlignmentStrategy.lazyWrapFiltered(
            { this.getUserData(PARAMETERS_ALIGNMENT) },
            TokenSet.create(RET_TYPE, WHERE_CLAUSE))

    PAT_ENUM ->
        RustAlignmentStrategy.wrapCond { c, p -> this.getUserData(INDENT_MET_LBRACE) == true && !c.isBlockDelim(p) }

    else -> RustAlignmentStrategy.NULL_STRATEGY
}
