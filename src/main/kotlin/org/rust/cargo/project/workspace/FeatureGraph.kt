/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.workspace

import org.rust.cargo.project.workspace.FeatureState.Disabled
import org.rust.cargo.project.workspace.FeatureState.Enabled
import org.rust.lang.utils.Node
import org.rust.lang.utils.PresentableGraph
import org.rust.lang.utils.PresentableNodeData
import org.rust.stdext.buildList
import org.rust.stdext.buildMap

enum class FeatureState {
    Enabled,
    Disabled;

    fun toBoolean(): Boolean = when (this) {
        Enabled -> true
        Disabled -> false
    }

    companion object {
        fun fromBoolean(enabled: Boolean) = when (enabled) {
            true -> Enabled
            false -> Disabled
        }
    }
}

data class FeatureData(val name: String, var state: FeatureState) : PresentableNodeData {
    override val text: String
        get() = "$name: $state"
}

typealias FeatureNode = Node<FeatureData, Unit>

class FeatureGraph private constructor(
    private val graph: PresentableGraph<FeatureData, Unit>,
    private val featureToNode: Map<String, FeatureNode> = emptyMap(),
    private val defaultFeatures: Set<String> = emptySet()
) {
    val state: Map<String, FeatureState>
        get() = buildMap {
            graph.forEachNode {
                put(it.data.name, it.data.state)
            }
        }

    val names: List<String> = buildList {
        graph.forEachNode { add(it.data.name) }
    }

    val currentDeps: MutableList<String> = mutableListOf()

    fun updateAllFeatures(state: FeatureState) {
        currentDeps.clear()

        when (state) {
            Enabled -> graph.forEachNode(::enableFeature)
            Disabled -> graph.forEachNode(::disableFeature)
        }
    }

    fun updateFeature(feature: String, state: FeatureState) {
        currentDeps.clear()

        val node = featureToNode[feature] ?: return
        when (state) {
            Enabled -> enableFeatureTransitively(node)
            Disabled -> disableFeatureTransitively(node)
        }
    }

    private fun enableFeatureTransitively(node: FeatureNode) {
        enableFeature(node)

        for (edge in graph.incomingEdges(node)) {
            val dependency = edge.source
            enableFeatureTransitively(dependency)
        }
    }

    private fun disableFeatureTransitively(node: FeatureNode) {
        disableFeature(node)

        for (edge in graph.outgoingEdges(node)) {
            val dependant = edge.target
            disableFeatureTransitively(dependant)
        }
    }

    private fun enableFeature(node: FeatureNode) {
        if (node.data.name.contains("/")) {
            currentDeps += node.data.name
        } else {
            node.data.state = Enabled
        }
    }

    private fun disableFeature(node: FeatureNode) {
        if (node.data.name.contains("/")) {
            currentDeps += node.data.name
        } else {
            node.data.state = Disabled
        }
    }

    companion object {
        fun buildFor(features: Map<String, List<String>>, defaultFeatures: Set<String>): FeatureGraph {
            val graph = PresentableGraph<FeatureData, Unit>()
            val featureToNode = hashMapOf<String, FeatureNode>()

            fun addFeatureIfNeeded(feature: String) {
                if (feature in featureToNode) return
                val state = FeatureState.fromBoolean(feature in defaultFeatures)
                val newNode = graph.addNode(FeatureData(feature, state))
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

            return FeatureGraph(graph, featureToNode, defaultFeatures)
        }

        val Empty: FeatureGraph
            get() = FeatureGraph(PresentableGraph())
    }
}
