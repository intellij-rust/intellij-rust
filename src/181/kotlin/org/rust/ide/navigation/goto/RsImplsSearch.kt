/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.util.Processor

class RsImplsSearch : RsImplsSearchBase() {
    override fun processQuery(queryParameters: DefinitionsScopedSearch.SearchParameters, consumer: Processor<PsiElement>) {
        processQueryInner(queryParameters, consumer)
    }
}
