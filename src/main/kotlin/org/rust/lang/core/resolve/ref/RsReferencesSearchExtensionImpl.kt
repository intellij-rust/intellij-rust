/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsTupleFieldDecl
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.psi.ext.ancestorStrict

class RsReferencesSearchExtensionImpl : QueryExecutorBase<RsReference, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in RsReference>) {
        val element = queryParameters.elementToSearch
        when {
            element is RsTupleFieldDecl -> {
                val elementOwnerStruct = element.ancestorStrict<RsFieldsOwner>()!!
                val elementIndex = elementOwnerStruct.tupleFields!!.tupleFieldDeclList.indexOf(element)
                queryParameters.optimizer.searchWord(
                    elementIndex.toString(),
                    elementOwnerStruct.useScope,
                    UsageSearchContext.IN_CODE,
                    false,
                    element
                )
            }
            element is RsFile && element.getOwnedDirectory() != null ->
                queryParameters.optimizer.searchWord(
                    element.getOwnedDirectory()!!.name,
                    element.useScope,
                    true,
                    element)
        }
    }

}
