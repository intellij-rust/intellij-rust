package org.rust.ide.navigation.goto

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.util.Processor
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.searchForImplementations

class RsTraitImplementationsSearch : QueryExecutorBase<PsiElement, DefinitionsScopedSearch.SearchParameters>(/* readAction = */ true) {
    override fun processQuery(queryParameters: DefinitionsScopedSearch.SearchParameters, consumer: Processor<PsiElement>) {
        val trait = queryParameters.element as? RsTraitItem ?: return
        trait.searchForImplementations().forEach(Processor { consumer.process(it) })
    }
}
