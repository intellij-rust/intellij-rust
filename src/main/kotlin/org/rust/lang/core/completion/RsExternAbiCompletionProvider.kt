/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.ide.annotator.fixes.AddFeatureAttributeFix
import org.rust.lang.core.FeatureAvailability.AVAILABLE
import org.rust.lang.core.FeatureAvailability.CAN_BE_ADDED
import org.rust.lang.core.or
import org.rust.lang.core.psi.RsElementTypes.RAW_STRING_LITERAL
import org.rust.lang.core.psi.RsElementTypes.STRING_LITERAL
import org.rust.lang.core.psi.RsExternAbi
import org.rust.lang.core.withSuperParent
import org.rust.lang.utils.SUPPORTED_CALLING_CONVENTIONS

object RsExternAbiCompletionProvider : RsCompletionProvider() {
    override val elementPattern: ElementPattern<out PsiElement>
        get() = psiElement(STRING_LITERAL)
            .or(psiElement(RAW_STRING_LITERAL))
            .withSuperParent<RsExternAbi>(2)

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val file = parameters.originalFile
        val lookups = SUPPORTED_CALLING_CONVENTIONS.mapNotNull { (conventionName, compilerFeature) ->
            val availability = compilerFeature?.availability(file) ?: AVAILABLE
            if (availability != AVAILABLE && availability != CAN_BE_ADDED) return@mapNotNull null
            var builder = LookupElementBuilder.create(conventionName)
            if (compilerFeature != null) {
                builder = builder.withInsertHandler { _, _ ->
                    if (compilerFeature.availability(file) == CAN_BE_ADDED) {
                        AddFeatureAttributeFix.addFeatureAttribute(file.project, file, compilerFeature.name)
                    }
                }
            }
            builder
        }
        result.addAllElements(lookups)
    }
}
