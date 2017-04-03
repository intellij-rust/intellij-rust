package org.rust.lang.core.resolve

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.psi.ext.RsLabeledExpression
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.psi.ext.isPredefined


object ResolveEngine {
    fun resolveLabel(label: RsLabel): RsLabelDecl? =
        label.ancestors
            .takeWhile { it !is RsLambdaExpr && it !is RsFunction }
            .mapNotNull { (it as? RsLabeledExpression)?.labelDecl }
            .find { it.name == label.quoteIdentifier.text }

    fun resolveLifetime(lifetimeRef: RsLifetime): RsLifetimeDecl? =
        if (lifetimeRef.isPredefined) {
            null
        } else {
            lifetimeRef.ancestors
                .mapNotNull {
                    when (it) {
                        is RsGenericDeclaration -> it.typeParameterList?.lifetimeParameterList
                        is RsForInType -> it.forLifetimes.lifetimeParameterList
                        is RsPolybound -> it.forLifetimes?.lifetimeParameterList
                        else -> null
                    }
                }
                .flatMap { it.lifetimeDecls }
                .find { it.name == lifetimeRef.quoteIdentifier.text }
        }
}

private val List<RsLifetimeParameter>.lifetimeDecls: Sequence<RsLifetimeDecl>
    get() = asSequence().map { it.lifetimeDecl }
