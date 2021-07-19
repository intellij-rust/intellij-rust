/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.structure

import com.intellij.ide.util.treeView.smartTree.ActionPresentation
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData
import com.intellij.ide.util.treeView.smartTree.Filter
import com.intellij.ide.util.treeView.smartTree.TreeElement
import org.rust.RsBundle
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.macros.isExpandedFromMacro

class RsMacroExpandedFilter : Filter {
    override fun getPresentation(): ActionPresentation = ActionPresentationData(
        RsBundle.message("structure.view.show.macro.expanded"),
        null,
        RsIcons.MACRO_EXPANSION,
    )

    override fun getName(): String = ID

    override fun isVisible(treeNode: TreeElement?): Boolean {
        val psi = (treeNode as? RsStructureViewElement)?.value ?: return true
        return !psi.isExpandedFromMacro
    }

    override fun isReverted(): Boolean = true

    companion object {
        const val ID = "STRUCTURE_VIEW_MACRO_EXPANDED_FILTER"
    }
}
