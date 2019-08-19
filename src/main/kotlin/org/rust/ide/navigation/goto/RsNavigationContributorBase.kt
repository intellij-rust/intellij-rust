/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.GotoClassContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.ArrayUtilRt
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsQualifiedNamedElement
import org.rust.lang.core.psi.ext.qualifiedName
import java.util.*

abstract class RsNavigationContributorBase<T> protected constructor(
    private val indexKey: StubIndexKey<String, T>,
    private val clazz: Class<T>
) : ChooseByNameContributorEx,
    GotoClassContributor where T : NavigationItem, T : RsNamedElement {

    override fun processNames(processor: Processor<String>, scope: GlobalSearchScope, filter: IdFilter?) {
        StubIndex.getInstance().processAllKeys(indexKey, processor, scope, filter)
    }

    override fun processElementsWithName(name: String, processor: Processor<NavigationItem>, parameters: FindSymbolParameters) {
        StubIndex.getInstance().processElements(
            indexKey,
            name,
            parameters.project,
            parameters.searchScope,
            parameters.idFilter,
            clazz,
            processor
        )
    }

    // BACKCOMPAT 2019.1
    override fun getNames(project: Project, includeNonProjectItems: Boolean): Array<String> {
        val result = ArrayList<String>()
        processNames({ result.add(it) }, FindSymbolParameters.searchScopeFor(project, includeNonProjectItems), null)
        return ArrayUtilRt.toStringArray(result)
    }

    // BACKCOMPAT 2019.1
    override fun getItemsByName(name: String, pattern: String, project: Project, includeNonProjectItems: Boolean): Array<NavigationItem> {
        val result = ArrayList<NavigationItem>()
        val params = FindSymbolParameters("", "", FindSymbolParameters.searchScopeFor(project, includeNonProjectItems), null)
        processElementsWithName(name, { result.add(it) }, params)
        return if (result.isEmpty()) NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY else result.toTypedArray()
    }

    override fun getQualifiedName(item: NavigationItem?): String? = (item as? RsQualifiedNamedElement)?.qualifiedName

    override fun getQualifiedNameSeparator(): String = "::"
}
