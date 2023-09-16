/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.fixes.ElideLifetimesFix
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsWithMacrosInspectionVisitor
import org.rust.ide.inspections.lints.ReferenceLifetime.*
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.rawType
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.regions.ReStatic
import org.rust.lang.core.types.ty.TyTraitObject
import org.rust.openapiext.forEachChild
import org.rust.stdext.chain

/**
 * Checks for lifetime annotations which can be removed by relying on lifetime elision.
 * Corresponds to needless_lifetimes lint from Rust Clippy.
 */
class RsNeedlessLifetimesInspection : RsLintInspection() {

    override fun getLint(element: PsiElement): RsLint = RsLint.NeedlessLifetimes

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsWithMacrosInspectionVisitor() {
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override fun visitFunction2(fn: RsFunction) {
            if (couldUseElision(fn)) {
                registerProblem(holder, fn)
            }
        }
    }

    private fun registerProblem(holder: RsProblemsHolder, fn: RsFunction) {
        holder.registerLintProblem(
            fn,
            RsBundle.message("inspection.message.explicit.lifetimes.given.in.parameter.types.where.they.could.be.elided"),
            TextRange(
                fn.fn.startOffsetInParent,
                fn.block?.getPrevNonCommentSibling()?.endOffsetInParent ?: fn.identifier.endOffsetInParent
            ),
            RsLintHighlightingType.WEAK_WARNING,
            listOf(ElideLifetimesFix(fn))
        )
    }
}

/**
 * There are two scenarios where elision works:
 * - no output references, all input references have different LT
 * - output references, exactly one input reference with same LT
 * All lifetimes must be unnamed, 'static or defined without bounds on the level of the current item.
 * Note: async fn syntax does not allow lifetime elision outside of & and &mut references.
 */
private fun couldUseElision(fn: RsFunction): Boolean {
    if (hasWhereLifetimes(fn.whereClause)) return false

    val typeParametersBounds = fn.typeParameters.flatMap { it.bounds }.map { it.bound }
    if (typeParametersBounds.any { hasNamedReferenceLifetime(it) }) return false

    val (inputLifetimeCollector, outputLifetimesCollector) = collectLifetimesFromFnSignature(fn) ?: return false
    val inputLifetimes = inputLifetimeCollector.lifetimes
    val outputLifetimes = outputLifetimesCollector.lifetimes

    if (fn.isAsync && (inputLifetimeCollector.hasLifetimeOutsideRef || outputLifetimesCollector.hasLifetimeOutsideRef)) return false

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
        inputLifetimes.size == 1 || fn.selfParameter?.isRefLike == true && areInputsDistinct -> {
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

private class LifetimesCollector(val isForInputParams: Boolean = false) : RsRecursiveVisitor() {
    var abort: Boolean = false
    var hasLifetimeOutsideRef: Boolean = false
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
        val type = ref.rawType
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

    override fun visitLifetime(lifetime: RsLifetime) {
        hasLifetimeOutsideRef = hasLifetimeOutsideRef || lifetime.parent !is RsRefLikeType
        record(lifetime)
    }

    override fun visitElement(element: RsElement) {
        if (abort) return
        if (element is RsItemElement) return  // ignore nested items
        if (processFnPointerOrFnTrait(element)) return
        element.forEachChild { it.accept(this) }
    }

    private fun processFnPointerOrFnTrait(element: RsElement): Boolean {
        val isFnPointer = element is RsFnPointerType  // `fn(...)`
        val isFnTrait = element is RsPath && element.valueParameterList != null  // `Fn(...)`
        if (!isFnPointer && !isFnTrait) return false

        // Ignore parameter lifetimes `Fn(&'a i32)` but process path lifetimes `Fn(Self::AssocType<'a>)`
        for (path in element.descendantsOfType<RsPath>()) {
            collectAnonymousLifetimes(path)
            path.typeArgumentList?.lifetimeList?.forEach(::record)
        }
        return true
    }

    private fun record(lifetime: RsLifetime?) {
        lifetimes.add(lifetime.typedName.toReferenceLifetime())
    }

    private fun collectAnonymousLifetimes(path: RsPath) {
        if (path.lifetimeArguments.isNotEmpty()) return
        when (val resolved = path.reference?.resolve()) {
            is RsStructItem, is RsEnumItem, is RsTraitItem, is RsTypeAlias -> {
                val declaration = resolved as RsGenericDeclaration
                repeat(declaration.lifetimeParameters.size) {
                    record(null)
                }
            }
        }
    }
}

private class BodyLifetimeChecker : RsWithMacrosInspectionVisitor() {
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

private fun collectLifetimesFromFnSignature(fn: RsFunction): Pair<LifetimesCollector, LifetimesCollector>? {
    // these will collect all the lifetimes for references in arg/return types
    val inputCollector = LifetimesCollector(isForInputParams = true)
    val outputCollector = LifetimesCollector()

    // extract lifetimes in input argument types
    fn.valueParameterList?.let { list ->
        list.selfParameter?.accept(inputCollector)
        list.valueParameterList.forEach { it.typeReference?.accept(inputCollector) }
    }
    if (inputCollector.abort) return null

    // extract lifetimes in output type
    fn.retType?.typeReference?.accept(outputCollector)
    if (outputCollector.abort) return null

    return inputCollector to outputCollector
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
        if (!allowedLifetimes.containsAll(collector.lifetimes)) return true
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

/** The lifetime of a &-reference. */
private sealed class ReferenceLifetime {
    object Unnamed : ReferenceLifetime()
    object Static : ReferenceLifetime()
    data class Named(val name: String) : ReferenceLifetime()
}

private fun LifetimeName.toReferenceLifetime(): ReferenceLifetime =
    when (this) {
        is LifetimeName.Parameter -> Named(name)
        LifetimeName.Static -> Static
        LifetimeName.Implicit, LifetimeName.Underscore -> Unnamed
    }

/**
 * Includes:
 * - `&self` and `&mut self` (see [isRef])
 * - `self: &Self` and `self: &mut Self`
 * - `self: Box<&Self>`, `self: Rc<&Self>`, `self: Arc<&Self>`, `self: Pin<&Self>`
 * - `self: Rc<Box<&Self>>` and other combinations
 */
val RsSelfParameter.isRefLike: Boolean
    get() {
        if (isRef) return true
        // Ideally, we should check the presence of `&Self` possible wrapped in `Box`, `Arc`, etc,
        // But anything else is anyway not allowed - https://doc.rust-lang.org/error_codes/E0307.html,
        val typeReference = typeReference ?: return false
        return typeReference.descendantsOfTypeOrSelf<RsRefLikeType>().any { it.and != null }
    }

fun RsFunction.hasMissingLifetimes(): Boolean {
    if (retType == null) return false
    if (selfParameter?.isRefLike == true) return false
    val (inputLifetimes, outputLifetimes) = collectLifetimesFromFnSignature(this) ?: return false
    return outputLifetimes.lifetimes.any { it is Unnamed } && inputLifetimes.lifetimes.size != 1
}
