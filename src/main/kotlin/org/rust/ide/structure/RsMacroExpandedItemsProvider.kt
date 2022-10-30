/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.structure

import com.intellij.ide.util.FileStructureNodeProvider
import com.intellij.ide.util.treeView.smartTree.ActionPresentation
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.openapi.actionSystem.Shortcut
import org.rust.RsBundle
import org.rust.ide.icons.RsIcons

class RsMacroExpandedItemsProvider : FileStructureNodeProvider<TreeElement> {
    override fun getName(): String = ID
    override fun getCheckBoxText(): String = RsBundle.message("structure.view.show.macro.expanded.checkbox")
    override fun getPresentation(): ActionPresentation =
        ActionPresentationData(RsBundle.message("structure.view.show.macro.expanded"), null, RsIcons.MACRO2)
    override fun getShortcut(): Array<Shortcut> = emptyArray()

    override fun provideNodes(element: TreeElement): Collection<TreeElement> {
        if (element !is RsStructureViewElement) return emptyList()
        return element.getChildren(showMacroExpansions = true)
    }

    companion object {
        const val ID: String = "SHOW_RUST_MACRO_EXPANDED_ITEMS"
    }
}
