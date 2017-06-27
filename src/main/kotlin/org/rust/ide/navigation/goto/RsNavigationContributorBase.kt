/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.GotoClassContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsQualifiedNamedElement
import org.rust.lang.core.psi.ext.qualifiedName

abstract class RsNavigationContributorBase<T> protected constructor(
    private val indexKey: StubIndexKey<String, T>,
    private val clazz: Class<T>
) : ChooseByNameContributor,
    GotoClassContributor where T : NavigationItem, T : RsNamedElement {

    override fun getNames(project: Project?, includeNonProjectItems: Boolean): Array<out String> {
        project ?: return emptyArray()
        return StubIndex.getInstance().getAllKeys(indexKey, project).toTypedArray()
    }

    override fun getItemsByName(name: String?,
                                pattern: String?,
                                project: Project?,
                                includeNonProjectItems: Boolean): Array<out NavigationItem> {

        if (project == null || name == null) {
            return emptyArray()
        }
        val scope = if (includeNonProjectItems)
            GlobalSearchScope.allScope(project)
        else
            GlobalSearchScope.projectScope(project)

        return StubIndex.getElements(indexKey, name, project, scope, clazz).toTypedArray<NavigationItem>()
    }

    override fun getQualifiedName(item: NavigationItem?): String? = (item as? RsQualifiedNamedElement)?.qualifiedName

    override fun getQualifiedNameSeparator(): String = "::"
}
