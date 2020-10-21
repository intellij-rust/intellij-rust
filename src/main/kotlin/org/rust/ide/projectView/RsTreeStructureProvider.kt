/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.projectView

import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.roots.ModuleJdkOrderEntry
import org.rust.ide.sdk.RsSdkTypeBase.Companion.RUST_SDK_ID_NAME
import org.rust.lang.RsConstants
import org.rust.lang.core.psi.RsFile

/**
 * Moves `mod.rs` files on top and hides Rust SDK library
 */
class RsTreeStructureProvider : TreeStructureProvider, DumbAware {
    override fun modify(parent: AbstractTreeNode<*>, children: Collection<AbstractTreeNode<*>>, settings: ViewSettings?): Collection<AbstractTreeNode<*>> {
        if (children.any { it is PsiFileNode && it.value?.name == RsConstants.MOD_RS_FILE }) {
            return children.map { child ->
                if (child is PsiFileNode && child.value is RsFile) {
                    RsPsiFileNode(child, settings)
                } else {
                    child
                }
            }
        }

        if (parent is ExternalLibrariesNode) {
            return children.filterNot { isRustSdkNode(it) }
        }

        return children
    }

    companion object {
        private fun isRustSdkNode(node: AbstractTreeNode<*>): Boolean {
            if (node !is NamedLibraryElementNode) return false
            val entry = node.value?.orderEntry as? ModuleJdkOrderEntry ?: return false
            return entry.jdkTypeName == RUST_SDK_ID_NAME
        }
    }
}

private class RsPsiFileNode(original: PsiFileNode, viewSettings: ViewSettings?)
    : PsiFileNode(original.project, original.value, viewSettings) {
    override fun getSortKey(): Int = if (value.name == RsConstants.MOD_RS_FILE) -1 else 0
}
