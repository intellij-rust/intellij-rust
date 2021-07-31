/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.structure

import com.intellij.ide.util.treeView.smartTree.ActionPresentation
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData
import com.intellij.ide.util.treeView.smartTree.Sorter
import org.rust.RsBundle
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.ext.RsVisibility
import org.rust.lang.core.psi.ext.RsVisible

class RsVisibilitySorter : Sorter {
    private enum class Order {
        Public,
        Restricted,
        Private,
        Unknown;

        companion object {
            fun fromVisibility(vis: RsVisibility): Order {
                return when (vis) {
                    RsVisibility.Public -> Public
                    RsVisibility.Private -> Private
                    is RsVisibility.Restricted -> Restricted
                }
            }
        }
    }

    private fun getOrdering(x: Any?): Order {
        val psi = (x as? RsStructureViewElement)?.value
        val visibility = (psi as? RsVisible)?.visibility
        return if (visibility != null) Order.fromVisibility(visibility) else Order.Unknown
    }

    override fun getPresentation(): ActionPresentation = ActionPresentationData(
        RsBundle.message("structure.view.sort.visibility"),
        null,
        RsIcons.VISIBILITY_SORT,
    )

    override fun getName(): String = ID

    override fun getComparator(): java.util.Comparator<*> = Comparator<Any> { p0, p1 ->
        val ord0 = getOrdering(p0)
        val ord1 = getOrdering(p1)
        ord0.compareTo(ord1)
    }

    override fun isVisible(): Boolean = true

    companion object {
        const val ID = "STRUCTURE_VIEW_VISIBILITY_SORTER"
    }
}
