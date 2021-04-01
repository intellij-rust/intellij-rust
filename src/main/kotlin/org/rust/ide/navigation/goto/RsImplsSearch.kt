/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.util.Processor
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.RsAbstractable
import org.rust.lang.core.psi.ext.searchForImplementations
import org.rust.openapiext.filterQuery

class RsImplsSearch : QueryExecutorBase<PsiElement, DefinitionsScopedSearch.SearchParameters>(/* readAction = */ true) {
    override fun processQuery(queryParameters: DefinitionsScopedSearch.SearchParameters, consumer: Processor<in PsiElement>) {
        val query = when (val psi = queryParameters.element) {
            is RsStructItem -> psi.searchForImplementations()
            is RsEnumItem -> psi.searchForImplementations()
            is RsTraitItem -> psi.searchForImplementations()
            is RsAbstractable -> psi.searchForImplementations()
            else -> return
        }.filterQuery { it != null }
        query.forEach(Processor { consumer.process(it) })
    }
}
