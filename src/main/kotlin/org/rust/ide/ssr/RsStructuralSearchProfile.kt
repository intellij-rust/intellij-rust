/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.ssr

import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.dupLocator.util.NodeFilter
import com.intellij.lang.Language
import com.intellij.lang.parser.GeneratedParserUtilBase.DummyBlock
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.structuralsearch.MalformedPatternException
import com.intellij.structuralsearch.StructuralSearchProfile
import com.intellij.structuralsearch.impl.matcher.CompiledPattern
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions
import com.intellij.structuralsearch.plugin.ui.Configuration
import org.rust.RsBundle
import org.rust.ide.experiments.RsExperiments
import org.rust.ide.template.RsContextType
import org.rust.lang.RsFileType
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.resolve.ref.RsReference
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

    override fun createPatternTree(
        text: String,
        context: PatternTreeContext,
        fileType: LanguageFileType,
        language: Language,
        contextId: String?,
        project: Project,
        physical: Boolean
    ): Array<PsiElement> {
        var patternTree = super.createPatternTree(text, context, fileType, language, contextId, project, physical)
        if (patternTree.firstOrNull() is PsiErrorElement
            || patternTree.size > 1 && patternTree[0] is LeafPsiElement && patternTree[1] is PsiErrorElement
            || patternTree.firstOrNull() is DummyBlock) {
            val factory = RsPsiFactory(project)
            patternTree = factory.createBlockExpr(text).firstChild.children
        }
//        for (element in patternTree) print(DebugUtil.psiToString(element, false))
        return patternTree
    }

    inner class RustValidator : RsRecursiveVisitor() {
        val invalid = NodeFilter {
            it is RsGenericDeclaration && it !is RsStructItem
                || it is RsMacro
                || it is RsAlias
                || it is RsStmt
                || it is RsExpr && it !is RsLitExpr
                || it is RsBlock
                || it is RsReference
                || it is RsPat
        }

        override fun visitElement(element: PsiElement) {
            if (invalid.accepts(element)) {
                throw MalformedPatternException(RsBundle.message("ssr.unsupported.search.template"))
            }
            super.visitElement(element)
        }
    }

    override fun checkSearchPattern(pattern: CompiledPattern) {
        val visitor = RustValidator()
        val nodes = pattern.nodes
        while (nodes.hasNext()) {
            nodes.current().accept(visitor)
            nodes.advance()
        }
        nodes.reset()
    }

    override fun checkReplacementPattern(project: Project, options: ReplaceOptions) {
        throw MalformedPatternException(RsBundle.message("ssr.unsupported.replace.template"))
    }

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
