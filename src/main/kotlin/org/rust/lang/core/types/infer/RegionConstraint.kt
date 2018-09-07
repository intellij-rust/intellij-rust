/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.types.infer.RegionConstraint.*
import org.rust.lang.core.types.infer.VerifyBound.AllBounds
import org.rust.lang.core.types.regions.ReEmpty
import org.rust.lang.core.types.regions.ReStatic
import org.rust.lang.core.types.regions.ReVar
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.TyProjection
import org.rust.lang.core.types.ty.TyTypeParameter

typealias VarInfos = SnapshotMap<ReVar, ReVarOrigin>

/** A constraint that influences the inference process. */
sealed class RegionConstraint {
    /** One region variable is subregion of another. */
    data class VarSubVar(val sub: ReVar, val sup: ReVar) : RegionConstraint()

    /** Concrete region is subregion of region variable. */
    data class RegSubVar(val sub: Region, val sup: ReVar) : RegionConstraint()

    /** Region variable is subregion of concrete region. */
    data class VarSubReg(val sub: ReVar, val sup: Region) : RegionConstraint()

    /** A constraint where neither side is a variable. */
    data class RegSubReg(val sub: Region, val sup: Region) : RegionConstraint()
}

/**
 * The full set of region constraints gathered up by the collector.
 * Describes constraints between the region variables and other regions, as well as other conditions that must be
 * verified, or assumptions that can be made.
 */
data class RegionConstraintData(
    /** Constraints of the form `A <= B`, where either `A` or `B` can be a region variable. */
    val constraints: SnapshotMap<RegionConstraint, SubRegionOrigin> = SnapshotMap(),

    /**
     * A "verify" is something that we need to verify after inference is done, but which does not directly affect
     * inference in any way. An example is a `A <= B` where neither `A` nor `B` are inference variables.
     */
    val verifys: SnapshotList<Verify> = SnapshotList(),

    /** A "given" is a relationship that is known to hold. */
    val givens: SnapshotSet<Pair<Region, ReVar>> = SnapshotSet()
) {
    fun isEmpty(): Boolean = constraints.isEmpty() && verifys.isEmpty() && givens.isEmpty()
}

class RegionConstraintCollector {
    /** For each [ReVar], the corresponding [ReVarOrigin]. */
    val varInfos: VarInfos = SnapshotMap()

    val data: RegionConstraintData = RegionConstraintData()

    /** Once all the constraints have been gathered, extract out the final data. */
    fun intoInfosAndData(): Pair<VarInfos, RegionConstraintData> {
        check(!varInfos.inSnapshot())
        return Pair(varInfos, data)
    }

    fun createReVar(origin: ReVarOrigin): ReVar {
        val variable = ReVar(varInfos.size)
        varInfos.put(variable, origin)
        return variable
    }

    /** Returns the origin for the given variable. */
    fun getVarOrigin(variable: ReVar): ReVarOrigin? = varInfos.get(variable)

    fun addConstraint(constraint: RegionConstraint, origin: SubRegionOrigin) {
        // Never overwrite an existing (constraint, origin) - only insert one if it isn't present in the map yet.
        // This prevents origins from outside the snapshot being replaced with "less informative" origins.
        if (!data.constraints.contains(constraint)) {
            data.constraints.put(constraint, origin)
        }
    }

    fun addVerify(verify: Verify) {
        val bound = verify.bound
        // skip no-op cases known to be satisfied
        if (bound is AllBounds && bound.bounds.isEmpty()) return
        data.verifys.add((verify))
    }

    fun addGiven(sub: Region, sup: ReVar) {
        data.givens.add(Pair(sub, sup))
    }

    fun makeSubRegion(origin: SubRegionOrigin, sub: Region, sup: Region) {
        when {
            sup is ReStatic -> Unit  // all regions are subregions of static, so we can ignore this
            sub is ReVar && sup is ReVar -> addConstraint(VarSubVar(sub, sup), origin)
            sup is ReVar -> addConstraint(RegSubVar(sub, sup), origin)
            sub is ReVar -> addConstraint(VarSubReg(sub, sup), origin)
            else -> addConstraint(RegSubReg(sub, sup), origin)
        }
    }

    fun verifyGenericBound(origin: SubRegionOrigin, kind: GenericKind, sub: Region, bound: VerifyBound) {
        addVerify(Verify(kind, origin, sub, bound))
    }

    fun startSnapshot(): Snapshot = CombinedSnapshot(
        varInfos.startSnapshot(),
        data.constraints.startSnapshot(),
        data.verifys.startSnapshot(),
        data.givens.startSnapshot()
    )
}

/**
 * The parameter type [kind] (or associated type) must outlive the [region].
 * [kind] is known to outlive [bound]. Therefore verify that `[region] <= [bound]`.
 * Inference variables may be involved (but this verification step doesn't influence inference).
 */
data class Verify(
    val kind: GenericKind,
    val origin: SubRegionOrigin,
    val region: Region,
    val bound: VerifyBound
)

sealed class GenericKind {
    data class Parameter(val ty: TyTypeParameter) : GenericKind()
    data class Projection(val ty: TyProjection) : GenericKind()
}

/**
 * When we introduce a verification step, we wish to test that a particular region (let's call it `'min`)
 * meets some bound. The bound is described the by the following grammar:
 */
sealed class VerifyBound {
    /** B = exists {R} --> some 'r in {R} must outlive 'min */
    data class AnyRegion(val regions: List<Region>) : VerifyBound() {
        override val mustHold: Boolean = regions.contains(ReStatic)
        override val cannotHold: Boolean = regions.isEmpty()

        override fun forEachRegion(action: (Region) -> Unit) = regions.forEach(action)
    }

    /** B = forall {R} --> all 'r in {R} must outlive 'min */
    data class AllRegions(val regions: List<Region>) : VerifyBound() {
        override val mustHold: Boolean = regions.isEmpty()
        override val cannotHold: Boolean = regions.contains(ReEmpty)

        override fun forEachRegion(action: (Region) -> Unit) = regions.forEach(action)
    }

    /** B = exists {B} --> 'min must meet some bound b in {B} */
    data class AnyBound(val bounds: List<VerifyBound>) : VerifyBound() {
        override val mustHold: Boolean = bounds.any { it.mustHold }
        override val cannotHold: Boolean = bounds.all { it.cannotHold }

        override fun forEachRegion(action: (Region) -> Unit) = bounds.forEach { it.forEachRegion(action) }
    }

    /** B = forall {B} --> 'min must meet all bounds b in {B} */
    data class AllBounds(val bounds: List<VerifyBound>) : VerifyBound() {
        override val mustHold: Boolean = bounds.all { it.mustHold }
        override val cannotHold: Boolean = bounds.any { it.cannotHold }

        override fun forEachRegion(action: (Region) -> Unit) = bounds.forEach { it.forEachRegion(action) }
    }

    abstract val mustHold: Boolean
    abstract val cannotHold: Boolean

    abstract fun forEachRegion(action: (Region) -> Unit)

    infix fun or(other: VerifyBound): VerifyBound =
        if (mustHold || other.cannotHold) {
            this
        } else if (cannotHold || other.mustHold) {
            other
        } else {
            AnyBound(listOf(this, other))
        }

    infix fun and(other: VerifyBound): VerifyBound =
        if (mustHold && other.mustHold || cannotHold && other.cannotHold) {
            this
        } else {
            AllBounds(listOf(this, other))
        }
}
