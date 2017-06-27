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
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.RsMod.Companion.MOD_RS

/**
 * Moves `mod.rs` files on top
 */
class RsTreeStructureProvider : TreeStructureProvider, DumbAware {
    override fun modify(parent: AbstractTreeNode<*>, children: Collection<AbstractTreeNode<*>>, settings: ViewSettings?): Collection<AbstractTreeNode<*>> {
        if (children.none { it is PsiFileNode && it.value?.name == MOD_RS }) {
            return children
        }

        return children.map { child ->
            if (child is PsiFileNode && child.value is RsFile) {
                RsPsiFileNode(child, settings)
            } else {
                child
            }
        }
    }

    override fun getData(selected: MutableCollection<AbstractTreeNode<Any>>?, dataName: String?): Any? = null
}

private class RsPsiFileNode(original: PsiFileNode, viewSettings: ViewSettings?)
    : PsiFileNode(original.project, original.value, viewSettings) {
    override fun getSortKey(): Int = if (value.name == MOD_RS) -1 else 0
}
