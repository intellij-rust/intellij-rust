/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.TextRange
import org.rust.ide.inspections.ReferenceLifetime.*
import org.rust.ide.inspections.fixes.ElideLifetimesFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.regions.ReStatic
import org.rust.lang.core.types.ty.TyTraitObject
import org.rust.lang.core.types.type
import org.rust.openapiext.forEachChild
import org.rust.stdext.chain

/**
 * Checks for lifetime annotations which can be removed by relying on lifetime elision.
 * Corresponds to needless_lifetimes lint from Rust Clippy.
 */
class RsNeedlessLifetimesInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsVisitor() {
        override fun visitFunction(fn: RsFunction) {
            if (couldUseElision(fn)) registerProblem(holder, fn)
        }
    }
}

/**
 * There are two scenarios where elision works:
 * - no output references, all input references have different LT
 * - output references, exactly one input reference with same LT
 * All lifetimes must be unnamed, 'static or defined without bounds on the level of the current item.
 */
private fun couldUseElision(fn: RsFunction): Boolean {
    if (hasWhereLifetimes(fn.whereClause)) return false

    val typeParametersBounds = fn.typeParameters.flatMap { it.bounds }.map { it.bound }
    if (typeParametersBounds.any { hasNamedReferenceLifetime(it) }) return false

    val (inputLifetimes, outputLifetimes) = collectLifetimesFromFnSignature(fn) ?: return false

    // no input lifetimes? easy case!
    if (inputLifetimes.isEmpty()) return false

    if (checkLifetimesUsedInBody(fn.block)) return false

    // check for lifetimes from higher scopes
    val allowedLifetimes = allowedLifetimesFrom(fn.lifetimeParameters)
    if (!inputLifetimes.chain(outputLifetimes).all { allowedLifetimes.contains(it) }) return false

    val areInputsDistinct = inputLifetimes.size == inputLifetimes.distinct().size

    // no output lifetimes, check distinctness of input lifetimes
    if (outputLifetimes.isEmpty()) {
        // only unnamed and static, ok
        if (inputLifetimes.none { it is Named }) return false
        // we have no output reference, so we only need all distinct lifetimes
        return areInputsDistinct
    }

    return when {
        outputLifetimes.distinct().size > 1 -> false
        inputLifetimes.size == 1 || fn.selfParameter?.isRef == true && areInputsDistinct -> {
            val input = inputLifetimes.first()
            val output = outputLifetimes.first()
            when {
                input is Named && output is Named && input.name == output.name -> true
                input is Named && output === Unnamed -> true
                input === Unnamed && output === Unnamed && inputLifetimes.any { it is Named } -> true
                else -> false  // different named lifetimes or something static going on
            }
        }
        else -> false
    }
}

private class LifetimesCollector(val isForInputParams: Boolean = false) : RsVisitor() {
    var abort: Boolean = false
    val lifetimes = mutableListOf<ReferenceLifetime>()

    override fun visitSelfParameter(selfParameter: RsSelfParameter) {
        selfParameter.lifetime?.let { visitLifetime(it) }
        if (selfParameter.isRef && selfParameter.lifetime.isElided) {
            record(null)
        }
        selfParameter.typeReference?.let { visitTypeReference(it) }
    }

    override fun visitRefLikeType(refLike: RsRefLikeType) {
        if (refLike.isRef && refLike.lifetime.isElided) {
            record(null)
        }
        super.visitRefLikeType(refLike)
    }

    override fun visitTypeReference(ref: RsTypeReference) {
        val type = ref.type
        if (type is TyTraitObject && (type.region is ReEarlyBound || type.region is ReStatic)) {
            abort = true
        }
        super.visitTypeReference(ref)
    }

    override fun visitTraitType(trait: RsTraitType) {
        for (polybound in trait.polyboundList) {
            polybound.bound.lifetime?.let { record(null) }
            if (isForInputParams) {
                abort = abort || hasNamedReferenceLifetime(polybound.bound)
            }
        }
        super.visitTraitType(trait)
    }

    override fun visitPath(path: RsPath) {
        collectAnonymousLifetimes(path)
        super.visitPath(path)
    }

    override fun visitLifetime(lifetime: RsLifetime) = record(lifetime)

    override fun visitElement(element: RsElement) {
        if (abort || element is RsItemElement /* ignore nested items */) return
        element.forEachChild { it.accept(this) }
    }

    private fun record(lifetime: RsLifetime?) {
        lifetimes.add(lifetime.typedName.toReferenceLifetime())
    }

    private fun collectAnonymousLifetimes(path: RsPath) {
        if (path.lifetimeArguments.isNotEmpty()) return
        when (val resolved = path.reference?.resolve()) {
            is RsStructItem, is RsTraitItem, is RsTypeAlias -> {
                val declaration = resolved as RsGenericDeclaration
                repeat(declaration.lifetimeParameters.size) {
                    record(null)
                }
            }
        }
    }
}

private class BodyLifetimeChecker : RsVisitor() {
    var lifetimesUsedInBody: Boolean = false
        private set

    override fun visitLifetime(lifetime: RsLifetime) {
        if (lifetime.typedName is LifetimeName.Parameter) {
            lifetimesUsedInBody = true
        }
    }

    override fun visitElement(element: RsElement) {
        if (lifetimesUsedInBody || element is RsItemElement /* ignore nested items */) return
        element.forEachChild { it.accept(this) }
    }
}

private fun collectLifetimesFromFnSignature(fn: RsFunction): Pair<List<ReferenceLifetime>, List<ReferenceLifetime>>? {
    // these will collect all the lifetimes for references in arg/return types
    val inputCollector = LifetimesCollector(true)
    val outputCollector = LifetimesCollector()

    // extract lifetimes in input argument types
    fn.valueParameterList?.let {
        it.selfParameter?.accept(inputCollector)
        it.valueParameterList.forEach { it.typeReference?.accept(inputCollector) }
    }
    if (inputCollector.abort) return null

    // extract lifetimes in output type
    fn.retType?.typeReference?.accept(outputCollector)
    if (outputCollector.abort) return null

    return Pair(inputCollector.lifetimes, outputCollector.lifetimes)
}

private fun allowedLifetimesFrom(lifetimeParameters: List<RsLifetimeParameter>): Set<ReferenceLifetime> {
    val allowedLifetimes = HashSet<ReferenceLifetime>()
    lifetimeParameters
        .filter { it.bounds.isEmpty() }
        .mapNotNull { it.name }
        .map { Named(it) }
        .toCollection(allowedLifetimes)
    allowedLifetimes.add(Unnamed)
    allowedLifetimes.add(Static)
    return allowedLifetimes
}

/** Are any lifetimes mentioned in the `where` clause? If yes, we don't try to reason about elision. */
private fun hasWhereLifetimes(whereClause: RsWhereClause?): Boolean {
    if (whereClause == null) return false
    for (predicate in whereClause.wherePredList) {
        if (predicate.lifetime != null) return true

        // a predicate like F: Trait or F: for<'a> Trait<'a>

        // check the type F, it may not contain LT refs
        val collector = LifetimesCollector()
        predicate.typeReference?.accept(collector)
        if (collector.lifetimes.isNotEmpty()) return true

        // if the bounds define new lifetimes, they are fine to occur
        val boundLifetimeParams = predicate.forLifetimes?.lifetimeParameterList.orEmpty()
        val allowedLifetimes = allowedLifetimesFrom(boundLifetimeParams)
        // now walk the bounds
        predicate.typeParamBounds?.polyboundList?.map { it.bound }?.forEach { it.accept(collector) }
        // and check that all lifetimes are allowed
        if (!allowedLifetimes.containsAll(collector.lifetimes)) return false
    }
    return false
}

private fun hasNamedReferenceLifetime(bound: RsBound): Boolean {
    val collector = LifetimesCollector()
    bound.accept(collector)
    return collector.lifetimes.any { it is Named }
}

/** @return true if an lifetime is used in the [body] */
private fun checkLifetimesUsedInBody(body: RsBlock?): Boolean {
    if (body == null) return false
    val checker = BodyLifetimeChecker()
    body.accept(checker)
    return checker.lifetimesUsedInBody
}

private fun registerProblem(holder: RsProblemsHolder, fn: RsFunction) {
    holder.registerProblem(
        fn,
        "Explicit lifetimes given in parameter types where they could be elided",
        ProblemHighlightType.WEAK_WARNING,
        TextRange(
            fn.fn.startOffsetInParent,
            fn.block?.getPrevNonCommentSibling()?.endOffsetInParent ?: fn.identifier.endOffsetInParent
        ),
        ElideLifetimesFix()
    )
}

/** The lifetime of a &-reference. */
private sealed class ReferenceLifetime {
    object Unnamed : ReferenceLifetime()
    object Static : ReferenceLifetime()
    data class Named(val name: String) : ReferenceLifetime()
}

private fun LifetimeName.toReferenceLifetime(): ReferenceLifetime =
    when (this) {
        is LifetimeName.Parameter -> ReferenceLifetime.Named(name)
        LifetimeName.Static -> ReferenceLifetime.Static
        LifetimeName.Implicit, LifetimeName.Underscore -> ReferenceLifetime.Unnamed
    }
