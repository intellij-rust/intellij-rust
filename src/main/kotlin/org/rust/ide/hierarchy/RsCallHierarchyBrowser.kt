/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hierarchy

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyBrowserManager
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.util.treeView.AlphaComparator
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.PsiElement
import com.intellij.ui.PopupHandler
import org.rust.ide.experiments.RsExperiments.CALL_HIERARCHY
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsQualifiedNamedElement
import org.rust.openapiext.isFeatureEnabled
import javax.swing.JTree

class RsCallHierarchyBrowser(element: PsiElement) :
    CallHierarchyBrowserBase(element.project, element) {
    override fun createTrees(trees: MutableMap<in String, in JTree>) {
        val group = ActionManager.getInstance().getAction(IdeActions.GROUP_CALL_HIERARCHY_POPUP) as ActionGroup
        val baseOnThisMethodAction = BaseOnThisMethodAction()

        val tree = createTree(false)
        PopupHandler.installPopupMenu(
            tree,
            group,
            ActionPlaces.CALL_HIERARCHY_VIEW_POPUP
        )
        baseOnThisMethodAction.registerCustomShortcutSet(
            ActionManager.getInstance().getAction(IdeActions.ACTION_CALL_HIERARCHY).shortcutSet,
            tree
        )
        trees[getCalleeType()] = tree
    }

    override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor): PsiElement? = descriptor.psiElement

    override fun isApplicableElement(element: PsiElement): Boolean {
        if (!isFeatureEnabled(CALL_HIERARCHY)) return false
        return element is RsFunction
    }

    override fun createHierarchyTreeStructure(type: String, psiElement: PsiElement): HierarchyTreeStructure? {
        if (psiElement !is RsQualifiedNamedElement) return null
        return when (type) {
            getCalleeType() -> RsCalleeTreeStructure(psiElement)
            else -> null
        }
    }

    override fun getComparator(): Comparator<NodeDescriptor<*>> {
        val state = HierarchyBrowserManager.getInstance(myProject).state
        return if (state != null && state.SORT_ALPHABETICALLY) AlphaComparator.INSTANCE else SourceComparator.INSTANCE
    }
}
