/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.projectView

import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.DumbAware
import org.rust.lang.RsConstants
import org.rust.lang.core.psi.RsFile

/**
 * Moves `mod.rs` files and crate roots on top
 */
class RsTreeStructureProvider : TreeStructureProvider, DumbAware {
    override fun modify(
        parent: AbstractTreeNode<*>,
        children: Collection<AbstractTreeNode<*>>,
        settings: ViewSettings?
    ): Collection<AbstractTreeNode<*>> = children.map { child ->
        if (child is PsiFileNode && child.value is RsFile) {
            RsPsiFileNode(child, settings)
        } else {
            child
        }
    }
}

private class RsPsiFileNode(
    original: PsiFileNode,
    viewSettings: ViewSettings?
) : PsiFileNode(original.project, original.value, viewSettings) {
    override fun getSortKey(): Int = when {
        value.name == RsConstants.MOD_RS_FILE -> -2
        (value as? RsFile)?.isCrateRoot == true -> -1
        else -> 0
    }
}
