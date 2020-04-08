/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.workspace

import org.rust.lang.utils.Node
import org.rust.lang.utils.PresentableGraph

private typealias FeaturesGraphInner = PresentableGraph<PackageFeature, Unit>
private typealias FeatureNode = Node<PackageFeature, Unit>

class FeatureGraph private constructor(
    private val graph: FeaturesGraphInner,
    private val featureToNode: Map<PackageFeature, FeatureNode>
) {
    /** Applies the specified function [f] to a freshly created [FeaturesView] and returns its state */
    fun apply(defaultState: FeatureState, f: FeaturesView.() -> Unit): Map<PackageFeature, FeatureState> =
        FeaturesView(defaultState).apply(f).state

    /** Mutable view of a [FeatureGraph] */
    inner class FeaturesView(defaultState: FeatureState) {
        val state: MutableMap<PackageFeature, FeatureState> = hashMapOf()

        init {
            for (feature in featureToNode.keys) {
                state[feature] = defaultState
            }
        }

        fun enableAll(features: Iterable<PackageFeature>) {
            for (feature in features) {
                enable(feature)
            }
        }

        fun disableAll(features: Iterable<PackageFeature>) {
            for (feature in features) {
                disable(feature)
            }
        }

        fun enable(feature: PackageFeature) {
            val node = featureToNode[feature] ?: return
            enableFeatureTransitively(node)
        }

        fun disable(feature: PackageFeature) {
            val node = featureToNode[feature] ?: return
            disableFeatureTransitively(node)
        }

        private fun enableFeatureTransitively(node: FeatureNode) {
            if (state[node.data] == FeatureState.Enabled) return

            state[node.data] = FeatureState.Enabled

            for (edge in graph.incomingEdges(node)) {
                val dependency = edge.source
                enableFeatureTransitively(dependency)
            }
        }

        private fun disableFeatureTransitively(node: FeatureNode) {
            if (state[node.data] == FeatureState.Disabled) return

            state[node.data] = FeatureState.Disabled

            for (edge in graph.outgoingEdges(node)) {
                val dependant = edge.target
                disableFeatureTransitively(dependant)
            }
        }

    }

    companion object {
        fun buildFor(features: Map<PackageFeature, List<PackageFeature>>): FeatureGraph {
            val graph = FeaturesGraphInner()
            val featureToNode = hashMapOf<PackageFeature, FeatureNode>()

            fun addFeatureIfNeeded(feature: PackageFeature) {
                if (feature in featureToNode) return
                val newNode = graph.addNode(feature)
                featureToNode[feature] = newNode
            }

            // Add nodes
            for (feature in features.keys) {
                addFeatureIfNeeded(feature)
            }

            // Add edges
            for ((feature, dependencies) in features) {
                for (dependency in dependencies) {
                    addFeatureIfNeeded(feature)
                    addFeatureIfNeeded(dependency)
                    val sourceNode = featureToNode[dependency]!!
                    val targetNode = featureToNode[feature]!!
                    graph.addEdge(sourceNode, targetNode, Unit)
                }
            }

            return FeatureGraph(graph, featureToNode)
        }

        val Empty: FeatureGraph
            get() = FeatureGraph(FeaturesGraphInner(), emptyMap())
    }
}

fun Map<PackageFeature, FeatureState>.associateByPackageRoot(): Map<PackageRoot, Map<FeatureName, FeatureState>> {
    val map: MutableMap<PackageRoot, MutableMap<String, FeatureState>> = hashMapOf()
    for ((feature, state) in this) {
        map.getOrPut(feature.pkg.rootDirectory) { hashMapOf() }[feature.name] = state
    }
    return map
}
