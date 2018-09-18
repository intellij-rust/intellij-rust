/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.types.infer.RegionConstraint.*
import org.rust.lang.core.types.infer.RegionResolutionError.*
import org.rust.lang.core.types.regions.*
import org.rust.lang.utils.Direction
import org.rust.lang.utils.Direction.INCOMING
import org.rust.lang.utils.Direction.OUTGOING
import org.rust.lang.utils.Graph
import org.rust.stdext.dequeOf
import java.util.*
import java.util.stream.Collectors
import kotlin.streams.asStream

/**
 * This function performs lexical region resolution given a complete set of constraints and var origins.
 * It performs a fixed-point iteration to find region values which satisfy all constraints, assuming such values can be found.
 * It returns the final values of all the vars as well as a set of errors that must be reported.
 */
fun resolveLexicalRegions(
    relations: RegionRelations,
    varInfos: VarInfos,
    data: RegionConstraintData
): LexicalRegionResolutions = LexicalResolver(relations, varInfos, data).inferVarValues()

/**
 * Contains the result of lexical region resolution.
 * Offers methods to lookup up the final value of a region var.
 */
class LexicalRegionResolutions(
    val values: MutableMap<ReVar, Region?>,
    private val errorRegion: Region
) {
    val errors: MutableList<RegionResolutionError> = mutableListOf()

    fun normalize(region: Region): Region = if (region is ReVar) resolveVar(region) else region
    private fun resolveVar(variable: ReVar): Region = values[variable] ?: errorRegion
}

data class RegionAndOrigin(val region: Region, val origin: SubRegionOrigin)

typealias RegionGraph = Graph<ReVar, RegionConstraint>

class LexicalResolver(
    private val relations: RegionRelations,
    private val varInfos: VarInfos,
    private val data: RegionConstraintData
) {
    private val varsCount: Int get() = varInfos.size

    fun inferVarValues(): LexicalRegionResolutions {
        val varData = constructVarData()
        val graph = constructGraph()
        expandGivens(graph)
        doExpansion(varData)
        collectErrors(varData, varData.errors)
        collectVarErrors(varData, graph, varData.errors)
        return varData
    }

    /**
     * Initially, the value for all vars is set to `'empty`, the empty region.
     * The [doExpansion] phase will grow this larger.
     */
    private fun constructVarData(): LexicalRegionResolutions {
        val map: MutableMap<ReVar, Region?> =
            varInfos.iterator().asSequence().asStream().collect(Collectors.toMap({ it.key }, { ReEmpty }))
        return LexicalRegionResolutions(map, ReStatic)
    }

    /**
     * Takes into account the transitive nature:
     *     Given 'c <= '0
     *     and   '0 <= '1
     *     then  'c <= '1
     */
    private fun expandGivens(graph: RegionGraph) {
        val seeds = data.givens.stream().collect(Collectors.toList())
        for ((region, variable) in seeds) {
            val seed = graph.getNode(variable.index)
            check(seed.data === variable)
            // While all things transitively reachable in the graph from the var (`'0` in the example above).
            for (node in graph.depthFirstTraversal(seed, OUTGOING)) {
                // The first N nodes correspond to the region variables. Other nodes correspond to constant regions.
                if (node.index < varsCount) {
                    // Add `'c <= '1`.
                    data.givens.add(Pair(region, node.data))
                }
            }
        }
    }

    private fun doExpansion(varValues: LexicalRegionResolutions) =
        iterateUntilFixedPoint { constraint ->
            when (constraint) {
                is RegSubVar -> {
                    val (subRegion, supVar) = constraint
                    val supRegion = varValues.values[supVar]
                    doExpansionStep(varValues, subRegion, supVar, supRegion)
                }
                is VarSubVar -> {
                    val (subVar, supVar) = constraint
                    val subRegion = varValues.values[subVar] ?: return@iterateUntilFixedPoint false
                    val supRegion = varValues.values[supVar]
                    doExpansionStep(varValues, subRegion, supVar, supRegion)
                }
                // These constraints are checked after expansion is done, in [collectErrors].
                is RegSubReg, is VarSubReg -> false
            }
        }

    private fun doExpansionStep(
        varValues: LexicalRegionResolutions,
        subRegion: Region,
        supVar: ReVar,
        supRegion: Region?
    ): Boolean {
        val expandedRegion = expandRegion(subRegion, supVar, supRegion)
        return if (expandedRegion != null) {
            varValues.values[supVar] = expandedRegion
            true
        } else {
            false
        }
    }

    private fun expandRegion(subRegion: Region, supVar: ReVar, supRegion: Region?): Region? {
        // Check if this relationship is implied by a given.
        if ((subRegion is ReEarlyBound || subRegion is ReFree) &&
            data.givens.contains(Pair(subRegion, supVar))) {
            return null
        }

        return supRegion?.let {
            val leastUpperBound = getLeastUpperBoundConcreteRegions(subRegion, supRegion)
            if (leastUpperBound != supRegion) leastUpperBound else null
        }
    }

    private fun getLeastUpperBoundConcreteRegions(region1: Region, region2: Region): Region {
        require(region1 !is ReVar && region2 !is ReVar) { "Invoked with non-concrete regions" }

        return when {
            // nothing lives longer than static
            region1 === ReStatic -> region1
            region2 === ReStatic -> region2

            // everything lives longer than empty
            region1 === ReEmpty -> region2
            region2 === ReEmpty -> region1

            region1 is ReScope && (region2 is ReEarlyBound || region2 is ReFree) ||
                (region1 is ReEarlyBound || region1 is ReFree) && region2 is ReScope -> {
                // A "free" region can be interpreted as "some region at least as big as free scope".
                // So, we can reasonably compare free regions and scopes:
                val freeScope = when {
                    region1 is ReEarlyBound -> relations.regionScopeTree.getEarlyFreeScope(region1)
                    region2 is ReEarlyBound -> relations.regionScopeTree.getEarlyFreeScope(region2)
                    region1 is ReFree -> relations.regionScopeTree.getFreeScope(region1)
                    region2 is ReFree -> relations.regionScopeTree.getFreeScope(region2)
                    else -> error("impossible")
                }

                val scope = checkNotNull((region1 as? ReScope)?.scope ?: (region2 as? ReScope)?.scope)
                val regionScope = relations.regionScopeTree.getLowestCommonAncestor(freeScope, scope)
                if (regionScope == freeScope) {
                    // if the free region's scope is bigger than the region scope, then the LUB is the free region
                    // itself:
                    return when {
                        region1 is ReScope -> region2
                        region2 is ReScope -> region1
                        else -> error("impossible")
                    }
                }

                // otherwise, we don't know what the free region is, so we must conservatively say the LUB is static:
                ReStatic
            }

            region1 is ReScope && region2 is ReScope -> {
                // The region corresponding to an outer block is a subtype of the region corresponding to an inner block
                val leastCommonBound = relations
                    .regionScopeTree
                    .getLowestCommonAncestor(region1.scope, region2.scope)
                ReScope(leastCommonBound)
            }

            (region1 is ReEarlyBound || region1 is ReFree) && (region2 is ReEarlyBound || region2 is ReFree) ->
                relations.getLeastUpperBoundOfFreeRegions(region1, region2)

            else -> error("impossible")
        }
    }

    /**
     * After expansion is complete, go and check upper bounds (i.e., cases where the region cannot grow larger than a
     * fixed point) and check that they are satisfied.
     */
    private fun collectErrors(varData: LexicalRegionResolutions, errors: MutableList<RegionResolutionError>) {
        loop@ for ((constraint, origin) in data.constraints) {
            when (constraint) {
                is RegSubVar, is VarSubVar -> {
                    // Expansion will ensure that these constraints hold. Ignore.
                }
                is RegSubReg -> {
                    if (relations.isSubRegionOf(constraint.sub, constraint.sup)) continue@loop
                    errors.add(ConcreteFailure(origin, constraint.sub, constraint.sup))
                }
                is VarSubReg -> {
                    val region = varData.values[constraint.sub] ?: continue@loop

                    // Do not report these errors immediately:
                    // instead, set the variable value to error and collect them later.
                    if (!relations.isSubRegionOf(region, constraint.sup)) {
                        varData.values[constraint.sub] = null
                    }
                }
            }

            for (verify in data.verifys) {
                val sub = varData.normalize(verify.region)
                if (sub is ReEmpty) continue@loop
                if (boundIsMet(verify.bound, varData, sub)) continue@loop
                errors.add(GenericBoundFailure(verify.origin, verify.kind, sub))
            }
        }
    }

    /** Go over the vars that were declared to be error vars and create a [RegionResolutionError] for each of them. */
    private fun collectVarErrors(
        varData: LexicalRegionResolutions,
        graph: RegionGraph,
        errors: MutableList<RegionResolutionError>
    ) {
        val duplicates = hashMapOf<ReVar, ReVar>()
        for ((variable, value) in varData.values) {
            if (value == null) {  // Inference impossible, this value contains inconsistent constraints.
                collectErrorForExpandingNode(graph, duplicates, variable, errors)
            }
        }

    }

    private fun constructGraph(): RegionGraph {
        val graph = RegionGraph()
        varInfos.iterator().asSequence()
            .map { it.key }
            .sortedBy { it.index }
            .forEach { graph.addNode(it) }

        val dummySource = graph.addNode(ReVar(Int.MIN_VALUE))
        val dummySink = graph.addNode(ReVar(Int.MAX_VALUE))

        fun ReVar.toNode() = graph.getNode(index)

        for ((constraint, _) in data.constraints) {
            when (constraint) {
                is VarSubVar -> graph.addEdge(constraint.sub.toNode(), constraint.sup.toNode(), constraint)
                is RegSubVar -> graph.addEdge(dummySource, constraint.sup.toNode(), constraint)
                is VarSubReg -> graph.addEdge(constraint.sub.toNode(), dummySink, constraint)
                is RegSubReg -> Unit  // this would be an edge from [dummySource] to [dummySink]; just ignore it.
            }
        }

        return graph
    }

    private data class WalkState(
        val set: MutableSet<ReVar>,
        val stack: Deque<ReVar>,
        val result: MutableList<RegionAndOrigin>,
        var duplicateFound: Boolean
    )

    private fun collectConcreteRegions(
        graph: RegionGraph,
        origin: ReVar,
        direction: Direction,
        duplicates: MutableMap<ReVar, ReVar>
    ): Pair<MutableList<RegionAndOrigin>, Boolean> {
        val state = WalkState(
            hashSetOf(origin),
            dequeOf(origin),
            mutableListOf(),
            false
        )

        // to start off the process, walk the source node in the direction specified
        processEdges(data, state, graph, origin, direction)

        while (state.stack.isNotEmpty()) {
            val variable = state.stack.pop()

            // check whether we've visited this node on some previous walk
            if (!duplicates.contains(variable)) {
                duplicates[variable] = origin
            } else if (duplicates[variable] != origin) {
                state.duplicateFound = true
            }

            processEdges(data, state, graph, origin, direction)
        }

        state.let { (_, _, result, duplicateFound) -> return Pair(result, duplicateFound) }
    }

    private fun processEdges(
        data: RegionConstraintData,
        state: WalkState,
        graph: RegionGraph,
        source: ReVar,
        direction: Direction
    ) {
        val sourceNode = graph.getNode(source.index)
        for (edge in graph.incidentEdges(sourceNode, direction)) {

            fun addRegion(region: Region) {
                val origin = checkNotNull(data.constraints.get(edge.data))
                state.result.add(RegionAndOrigin(region, origin))
            }

            when (edge.data) {
                is VarSubVar -> {
                    val opposite = if (edge.data.sub == source) edge.data.sup else edge.data.sub
                    if (state.set.add(opposite)) state.stack.push(opposite)
                }
                is RegSubVar -> addRegion(edge.data.sub)
                is VarSubReg -> addRegion(edge.data.sup)
                is RegSubReg -> error("Cannot reach reg-sub-reg edge in region inference post-processing")
            }
        }
    }

    private fun collectErrorForExpandingNode(
        graph: RegionGraph,
        duplicates: MutableMap<ReVar, ReVar>,
        variable: ReVar,
        errors: MutableList<RegionResolutionError>
    ) {
        // Errors in expanding nodes result from a lower-bound that is not contained by an upper-bound.
        val (lowerBounds, lowerDuplicateFound) = collectConcreteRegions(graph, variable, INCOMING, duplicates)
        if (lowerDuplicateFound) return
        val (upperBounds, upperDuplicateFound) = collectConcreteRegions(graph, variable, OUTGOING, duplicates)
        if (upperDuplicateFound) return

        // We place free regions first because we are special casing SubSupConflict(ReFree, ReFree) when reporting
        // error, and so the user will more likely get a specific suggestion.
        fun getRegionOrderKey(item: RegionAndOrigin): Int =
            when (item.region) {
                is ReEarlyBound -> 0
                is ReFree -> 1
                else -> 2
            }

        lowerBounds.sortBy { getRegionOrderKey(it) }
        upperBounds.sortBy { getRegionOrderKey(it) }

        for (lowerBound in lowerBounds) {
            for (upperBound in upperBounds) {
                if (relations.isSubRegionOf(lowerBound.region, upperBound.region)) continue
                val origin = checkNotNull(varInfos.get(variable))
                errors.add(SubSupConflict(
                    origin,
                    lowerBound.origin,
                    lowerBound.region,
                    upperBound.origin,
                    upperBound.region
                ))
                return
            }
        }
    }

    private fun iterateUntilFixedPoint(body: (RegionConstraint) -> Boolean) {
        var changed = true
        while (changed) {
            changed = false
            for ((constraint, _) in data.constraints) {
                val edgeChanged = body(constraint)
                if (edgeChanged) {
                    changed = true
                }
            }
        }
    }

    private fun boundIsMet(bound: VerifyBound, varValues: LexicalRegionResolutions, min: Region): Boolean =
        when (bound) {
            is VerifyBound.AnyRegion ->
                bound.regions.map { varValues.normalize(it) }.any { relations.isSubRegionOf(min, it) }
            is VerifyBound.AllRegions ->
                bound.regions.map { varValues.normalize(it) }.all { relations.isSubRegionOf(min, it) }
            is VerifyBound.AnyBound ->
                bound.bounds.any { boundIsMet(it, varValues, min) }
            is VerifyBound.AllBounds ->
                bound.bounds.any { boundIsMet(it, varValues, min) }
        }
}

sealed class RegionResolutionError {

    /** [origin] requires that `[sub] <= [sup]`, but this does not hold. */
    data class ConcreteFailure(
        val origin: SubRegionOrigin,
        val sub: Region,
        val sup: Region
    ) : RegionResolutionError()

    /** The [parameter] must be known to outlive the [region]. */
    data class GenericBoundFailure(
        val origin: SubRegionOrigin,
        val parameter: GenericKind,
        val region: Region
    ) : RegionResolutionError()

    /**
     * Could not infer a value for var because `[subRegion] <= var` (due to [subOrigin]) but
     * `var <= [supRegion]` (due to [supOrigin]) and `[subRegion] <= [supRegion]` does not hold.
     */
    data class SubSupConflict(
        val varOrigin: ReVarOrigin,
        val subOrigin: SubRegionOrigin,
        val subRegion: Region,
        val supOrigin: SubRegionOrigin,
        val supRegion: Region
    ) : RegionResolutionError()
}
