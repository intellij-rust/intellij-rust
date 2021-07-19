/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2.util

import org.rust.lang.core.resolve2.ModData
import org.rust.lang.core.resolve2.Visibility
import org.rust.lang.utils.Graph

class GlobImportGraph {
    private val graph: Graph<ModData, Visibility> = Graph()
    private val modDataToIndex: MutableMap<ModData, Int> = hashMapOf()

    private fun getIndex(modData: ModData): Int =
        modDataToIndex.computeIfAbsent(modData) { graph.addNode(modData).index }

    fun recordGlobImport(source: ModData, target: ModData, visibility: Visibility) {
        if (visibility.isInvisible) return
        val sourceIndex = getIndex(source)
        val targetIndex = getIndex(target)
        graph.addEdge(sourceIndex, targetIndex, visibility)
    }

    fun hasTransitiveGlobImport(source: ModData, target: ModData): Boolean {
        val sourceIndex = modDataToIndex[source] ?: return false
        val sourceNode = graph.getNode(sourceIndex)
        val accessibleMods = graph.depthFirstTraversal(sourceNode) {
            it.data.isVisibleFromMod(source)
        }
        return accessibleMods.any { it.data == target }
    }
}
