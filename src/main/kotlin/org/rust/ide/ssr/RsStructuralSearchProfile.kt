/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.ssr

import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.structuralsearch.StructuralSearchProfile
import com.intellij.structuralsearch.impl.matcher.CompiledPattern
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy
import com.intellij.structuralsearch.plugin.ui.Configuration
import org.rust.ide.experiments.RsExperiments
import org.rust.ide.template.RsContextType
import org.rust.lang.RsFileType
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsLifetime
import org.rust.openapiext.isFeatureEnabled

class RsStructuralSearchProfile : StructuralSearchProfile() {
    override fun isMyLanguage(language: Language): Boolean {
        if (!isFeatureEnabled(RsExperiments.SSR)) return false
        return language == RsLanguage
    }

    override fun getDefaultFileType(fileType: LanguageFileType?): LanguageFileType? {
        if (!isFeatureEnabled(RsExperiments.SSR)) return null
        return fileType ?: RsFileType
    }

    override fun getTemplateContextTypeClass(): Class<out TemplateContextType> = RsContextType::class.java

    override fun compile(elements: Array<out PsiElement>, globalVisitor: GlobalCompilingVisitor) {
        RsCompilingVisitor(globalVisitor).compile(elements)
    }

    override fun createMatchingVisitor(globalVisitor: GlobalMatchingVisitor): PsiElementVisitor = RsMatchingVisitor(globalVisitor)

    override fun getPredefinedTemplates(): Array<Configuration> {
        if (!isFeatureEnabled(RsExperiments.SSR)) return arrayOf()
        return RsPredefinedConfigurations.createPredefinedTemplates()
    }

    override fun isIdentifier(element: PsiElement?): Boolean = element?.node?.elementType == IDENTIFIER

    override fun createCompiledPattern(): CompiledPattern = RsCompiledPattern()

    companion object {
        const val TYPED_VAR_PREFIX: String = "_____"
    }
}

private class RsCompiledPattern : CompiledPattern() {
    init {
        strategy = object : MatchingStrategy {
            override fun continueMatching(start: PsiElement?): Boolean = start?.language == RsLanguage
            override fun shouldSkip(element: PsiElement?, elementToMatchWith: PsiElement?): Boolean = false
        }
    }

    override fun getTypedVarPrefixes(): Array<String> = arrayOf(RsStructuralSearchProfile.TYPED_VAR_PREFIX)

    override fun isTypedVar(str: String): Boolean = when {
        str.isEmpty() -> false
        str[0] == '@' -> str.drop(1).startsWith(RsStructuralSearchProfile.TYPED_VAR_PREFIX)
        else -> str.startsWith(RsStructuralSearchProfile.TYPED_VAR_PREFIX)
    }

    override fun getTypedVarString(element: PsiElement): String {
        val typedVarString = super.getTypedVarString(element)
        // TODO: implement lifetime identifier properly
        val modifiedString = when (element) {
            is RsLifetime -> typedVarString.drop(1)
            else -> typedVarString
        }
        return modifiedString.removePrefix("@")
    }
}
