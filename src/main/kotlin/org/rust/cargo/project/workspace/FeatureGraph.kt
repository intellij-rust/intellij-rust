/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.workspace

import org.rust.lang.utils.Node
import org.rust.lang.utils.PresentableGraph
import org.rust.lang.utils.PresentableNodeData

enum class FeatureState {
    Enabled,
    Disabled;

    fun toBoolean(): Boolean = when (this) {
        Enabled -> true
        Disabled -> false
    }
}

typealias FeatureNode = Node<PackageFeature, Unit>

data class PackageFeature(val pkg: CargoWorkspace.Package, val name: String) : PresentableNodeData {
    override val text: String
        get() = "$pkg.$name"
}

typealias WorkspaceFeaturesGraph = PresentableGraph<PackageFeature, Unit>

class FeatureGraph private constructor(
    private val graph: WorkspaceFeaturesGraph,
    private val featureToNode: Map<PackageFeature, FeatureNode>
) {
    /** Applies the specified function [f] to a freshly created [FeaturesView] and returns its state */
    fun apply(f: FeaturesView.() -> Unit): Map<PackageFeature, FeatureState> =
        FeaturesView().apply(f).state

    /** Applies the specified function [f] to a freshly created [FeaturesView] and returns its flatten state */
    fun applyFlat(f: FeaturesView.() -> Unit): Map<String, Map<String, FeatureState>> =
        FeaturesView().apply(f).stateFlat

    /** Mutable view of a [FeatureGraph] */
    inner class FeaturesView {
        val state: MutableMap<PackageFeature, FeatureState> = hashMapOf()

        val stateFlat: MutableMap<String, MutableMap<String, FeatureState>>
            get() {
                val map: MutableMap<String, MutableMap<String, FeatureState>> = hashMapOf()
                for ((feature, state) in state) {
                    map.getOrPut(feature.pkg.rootDirectory.toString()) { hashMapOf() }[feature.name] = state
                }
                return map
            }

        init {
            for (feature in featureToNode.keys) {
                state[feature] = FeatureState.Disabled
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
            state[node.data] = FeatureState.Enabled

            for (edge in graph.incomingEdges(node)) {
                val dependency = edge.source
                enableFeatureTransitively(dependency)
            }
        }

        private fun disableFeatureTransitively(node: FeatureNode) {
            state[node.data] = FeatureState.Disabled

            for (edge in graph.outgoingEdges(node)) {
                val dependant = edge.target
                disableFeatureTransitively(dependant)
            }
        }

    }

    companion object {
        fun buildFor(features: Map<PackageFeature, List<PackageFeature>>): FeatureGraph {
            val graph = PresentableGraph<PackageFeature, Unit>()
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
            get() = FeatureGraph(WorkspaceFeaturesGraph(), emptyMap())
    }
}
