/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hierarchy

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyBrowser
import com.intellij.ide.hierarchy.HierarchyProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsElement

class RsCallHierarchyProvider : HierarchyProvider {
    override fun getTarget(dataContext: DataContext): RsElement? =
        dataContext.getData(CommonDataKeys.PSI_ELEMENT) as? RsFunction

    override fun createHierarchyBrowser(target: PsiElement) = RsCallHierarchyBrowser(target)

    override fun browserActivated(hierarchyBrowser: HierarchyBrowser) {
        (hierarchyBrowser as RsCallHierarchyBrowser).changeView(CallHierarchyBrowserBase.CALLEE_TYPE)
    }
}
