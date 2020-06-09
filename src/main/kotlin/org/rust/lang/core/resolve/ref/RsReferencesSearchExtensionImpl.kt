/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.RequestResultProcessor
import com.intellij.psi.search.SingleTargetRequestResultProcessor
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import org.rust.lang.core.psi.RsExternCrateItem
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.RsTupleFieldDecl
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.psi.ext.RsMandatoryReferenceElement
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
                    queryParameters.effectiveSearchScope,
                    UsageSearchContext.IN_CODE,
                    false,
                    element
                )
            }
            element is RsFile && element.getOwnedDirectory() != null -> {
                queryParameters.optimizer.searchWord(
                    element.getOwnedDirectory()!!.name,
                    queryParameters.effectiveSearchScope,
                    true,
                    element)
            }
            element is RsMandatoryReferenceElement && (element is RsModDeclItem || element is RsExternCrateItem) -> {
                val module = element.reference.resolve() ?: return
                queryParameters.optimizer.searchWord(
                    element.referenceName,
                    queryParameters.effectiveSearchScope,
                    UsageSearchContext.IN_CODE,
                    true,
                    module,
                    FilteringSingleTargetRequestResultProcessor(module))
            }
        }
    }

    private class FilteringSingleTargetRequestResultProcessor(element: PsiElement) : RequestResultProcessor(element) {
        private val target = SingleTargetRequestResultProcessor(element)

        override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
            return target.processTextOccurrence(element, offsetInElement) { reference ->
                if (reference.element !is RsModDeclItem && reference.element !is RsExternCrateItem) {
                    consumer.process(reference)
                } else {
                    true
                }
            }
        }
    }
}
