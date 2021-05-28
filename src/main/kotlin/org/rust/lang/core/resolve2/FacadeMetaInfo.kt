/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.util.containers.map2Array
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve2.RsModInfoBase.RsModInfo

data class NamedItem(val name: String, val item: RsNamedElement)

/** List of items added to [context] by glob import to [this] */
fun RsMod.exportedItems(context: RsMod): List<NamedItem> {
    val (project, defMap, modData) = getModInfo(this) as? RsModInfo ?: return emptyList()
    val contextInfo = getModInfo(context) as? RsModInfo ?: return emptyList()
    return modData
        .getVisibleItems { it.isVisibleFromMod(contextInfo.modData) }
        .flatMap { (name, perNs) ->
            perNs.allVisItems().flatMap { (visItem, namespace) ->
                val items = visItem.toPsi(defMap, project, namespace)
                items.map { NamedItem(name, it) }
            }
        }
}

private fun PerNs.allVisItems(): Array<Pair<VisItem, Namespace>> =
    types.map2Array { it to Namespace.Types } +
        values.map2Array { it to Namespace.Values } +
        macros.map2Array { it to Namespace.Macros }
