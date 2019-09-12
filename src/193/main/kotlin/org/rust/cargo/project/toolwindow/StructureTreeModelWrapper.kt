/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.toolwindow

import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.openapi.Disposable
import com.intellij.ui.tree.StructureTreeModel

// BACKCOMPAT: 2019.1. Replace with StructureTreeModel
class StructureTreeModelWrapper<S : AbstractTreeStructure>(structure: S, parentDisposable: Disposable)
    : StructureTreeModel<S>(structure, parentDisposable)
