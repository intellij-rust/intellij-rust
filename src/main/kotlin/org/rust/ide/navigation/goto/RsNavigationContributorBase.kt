/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.navigation.NavigationItem
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapiext.isInternal
import com.intellij.psi.search.EverythingGlobalScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.ArrayUtilRt
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import org.rust.ide.search.RsWithMacrosScope
import org.rust.lang.core.macros.findMacroCallExpandedFrom
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsQualifiedNamedElement
import org.rust.lang.core.psi.ext.contextualFile
import org.rust.lang.core.psi.ext.qualifiedName
import java.util.*

abstract class RsNavigationContributorBase<T> protected constructor(
    private val indexKey: StubIndexKey<String, T>,
    private val clazz: Class<T>
) : NavigationContributorBase<T>() where T : NavigationItem, T : RsNamedElement {

    override fun processNamesInner(processor: Processor<String>, scope: GlobalSearchScope, filter: IdFilter?) {
        checkFilter(filter)
        StubIndex.getInstance().processAllKeys(
            indexKey,
            processor,
            scope.withMacrosScope(),
            null // see `checkFilter`
        )
    }

    override fun processElementsWithNameInner(name: String, processor: Processor<NavigationItem>, parameters: FindSymbolParameters) {
        checkFilter(parameters.idFilter)
        val originScope = parameters.searchScope
        StubIndex.getInstance().processElements(
            indexKey,
            name,
            parameters.project,
            originScope.withMacrosScope(),
            null, // see `checkFilter`
            clazz
        ) { element ->
            // Filter out elements expanded from macros that are not in the scope
            val macroVFile = element.findMacroCallExpandedFrom()?.contextualFile?.originalFile?.virtualFile
            if (macroVFile == null || macroVFile in originScope) {
                processor.process(element)
            } else {
                true
            }
        }
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

private fun GlobalSearchScope.withMacrosScope(): GlobalSearchScope {
    val project = project
    return if (project != null && this !is EverythingGlobalScope) RsWithMacrosScope(project, this) else this
}

private val LOG = Logger.getInstance(RsNavigationContributorBase::class.java)

/**
 * [IdFilter] exists only for optimization purposes and can safely be null. If it is not null, we should
 * refine it in the same way as a scope in [withMacrosScope]. But looks like in 2019.2 it's always null,
 * so I can't even test the solution. I decided to always use `null` as a filter and enable this check
 * (in the internal mode only) to catch the situation when it will become non null.
 */
private fun checkFilter(filter: IdFilter?) {
    if (isInternal && filter != null) {
        LOG.error("IdFilter is supposed to be null", Throwable())
    }
}
