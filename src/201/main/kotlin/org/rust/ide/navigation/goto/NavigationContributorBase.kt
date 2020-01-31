/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.GotoClassContributor
import com.intellij.navigation.NavigationItem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import org.rust.lang.core.psi.ext.RsNamedElement

// BACKCOMPAT: 2019.3 - merge this class into [RsNavigationContributorBase]
@Suppress("UNCHECKED_CAST")
abstract class NavigationContributorBase<T> : ChooseByNameContributorEx,
                                              GotoClassContributor where T : NavigationItem, T : RsNamedElement {
    final override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        processNamesInner(processor as Processor<String>, scope, filter)
    }

    final override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
        processElementsWithNameInner(name, processor as Processor<NavigationItem>, parameters)
    }

    protected abstract fun processElementsWithNameInner(name: String, processor: Processor<NavigationItem>, parameters: FindSymbolParameters)
    protected abstract fun processNamesInner(processor: Processor<String>, scope: GlobalSearchScope, filter: IdFilter?)
}
