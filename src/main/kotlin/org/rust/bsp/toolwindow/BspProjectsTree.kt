/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp.toolwindow

import com.intellij.ui.treeStructure.SimpleTree
import javax.swing.tree.TreeSelectionModel

open class BspProjectsTree : SimpleTree() {

    init {
        isRootVisible = false
        showsRootHandles = true
        emptyText.text = "There are no Rust projects to display."
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

    }

}

