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

    companion object {
        fun fromBoolean(enabled: Boolean) = when (enabled) {
            true -> Enabled
            false -> Disabled
        }
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
//    val state: Map<CargoWorkspace.Package, Map<String, FeatureState>>
//        get() = buildMap {
//            graph.forEachNode {
//                put(it.data., it.data.state)
//            }
//        }

//    val names: List<String> = buildList {
//        graph.forEachNode { add(it.data.name) }
//    }

    fun updater(): FeatureGraphUpdater = FeatureGraphUpdater(graph, featureToNode)

    companion object {
        fun buildFor(features: Map<PackageFeature, List<PackageFeature>>, defaultFeatures: Set<PackageFeature>): FeatureGraph {
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

class FeatureGraphUpdater(
    private val features: WorkspaceFeaturesGraph,
    private val featureToNode: Map<PackageFeature, FeatureNode>
) {
    val featuresState: MutableMap<PackageFeature, FeatureState> = hashMapOf()
    val featuresStateFlat: MutableMap<String, MutableMap<String, FeatureState>>
        get() {
            val map: MutableMap<String, MutableMap<String, FeatureState>> = hashMapOf()
            for ((feature, state) in featuresState) {
                map.getOrPut(feature.pkg.rootDirectory.toString()) { hashMapOf() }[feature.name] = state
            }
            return map
        }

    init {
        for (feature in featureToNode.keys) {
            featuresState[feature] = FeatureState.Disabled
        }
    }

    fun updateFeatures(userOverriddenFeatures: Map<PackageFeature, Boolean>) {
        for ((feature, state) in userOverriddenFeatures) {
            val node = featureToNode[feature] ?: return
            when (state) {
                true -> enableFeatureTransitively(node)
                false -> disableFeatureTransitively(node)
            }
        }
    }

    fun updateFeature(feature: PackageFeature, state: FeatureState) {
        val node = featureToNode[feature] ?: return
        when (state) {
            FeatureState.Enabled -> enableFeatureTransitively(node)
            FeatureState.Disabled -> disableFeatureTransitively(node)
        }
    }

    private fun enableFeatureTransitively(node: FeatureNode) {
        enableFeature(node)

        for (edge in features.incomingEdges(node)) {
            val dependency = edge.source
            enableFeatureTransitively(dependency)
        }
    }

    private fun disableFeatureTransitively(node: FeatureNode) {
        disableFeature(node)

        for (edge in features.outgoingEdges(node)) {
            val dependant = edge.target
            disableFeatureTransitively(dependant)
        }
    }

    private fun enableFeature(node: FeatureNode) {
        featuresState[node.data] = FeatureState.Enabled
    }

    private fun disableFeature(node: FeatureNode) {
        featuresState[node.data] = FeatureState.Disabled
    }
}
