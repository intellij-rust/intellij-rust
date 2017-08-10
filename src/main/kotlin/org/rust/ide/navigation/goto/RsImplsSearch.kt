/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.util.Processor
import org.rust.ide.annotator.RsImplsLineMarkerProvider

/**
 * See [org.rust.ide.annotator.RsImplsLineMarkerProvider]
 */
class RsImplsSearch : QueryExecutorBase<PsiElement, DefinitionsScopedSearch.SearchParameters>(/* readAction = */ true) {
    override fun processQuery(queryParameters: DefinitionsScopedSearch.SearchParameters, consumer: Processor<PsiElement>) {
        val (query, _) = RsImplsLineMarkerProvider.implsQuery(queryParameters.element) ?: return
        query.forEach(Processor { consumer.process(it) })
    }
}
