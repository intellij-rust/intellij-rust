/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.RsLanguage
import org.rust.lang.core.macros.FragmentKind
import org.rust.lang.core.macros.FragmentKind.*
import org.rust.lang.core.macros.MacroGraphWalker
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psiElement

object RsPartialMacroArgumentCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        fun addCompletions(fragment: RsCodeFragment, offset: Int) {
            val reference = fragment.findReferenceAt(offset) ?: return
            val referenceElement = reference.element as? RsReferenceElement ?: return
            val isSimplePath = RsCommonCompletionProvider.simplePathPattern.accepts(referenceElement.firstChild)

            RsCommonCompletionProvider.processElement(referenceElement, isSimplePath, null, hashSetOf(), parameters, result)

            if (RsPrimitiveTypeCompletionProvider.elementPattern.accepts(referenceElement.firstChild)) {
                RsPrimitiveTypeCompletionProvider.addCompletionVariants(parameters, context, result)
            }
        }

        val project = parameters.originalFile.project
        val position = parameters.position
        val macroCall = position.ancestorStrict<RsMacroArgument>()?.ancestorStrict<RsMacroCall>() ?: return
        val bodyTextRange = macroCall.bodyTextRange ?: return
        val macroCallBody = macroCall.macroBody ?: return
        val macro = macroCall.resolveToMacro() ?: return
        val graph = macro.graph ?: return
        val offsetInArgument = parameters.offset - bodyTextRange.startOffset

        val walker = MacroGraphWalker(project, graph, macroCallBody, offsetInArgument)
        val fragmentDescriptors = walker.run().takeIf { it.isNotEmpty() } ?: return
        val usedKinds = mutableSetOf<FragmentKind>()

        for ((fragmentText, caretOffsetInFragment, kind) in fragmentDescriptors) {
            if (kind in usedKinds) continue

            val codeFragment = when (kind) {
                Expr, Path -> RsExpressionCodeFragment(project, fragmentText, macroCall)
                Stmt -> RsStatementCodeFragment(project, fragmentText, macroCall)
                Ty -> RsTypeReferenceCodeFragment(project, fragmentText, macroCall)
                else -> null
            } ?: continue

            addCompletions(codeFragment, caretOffsetInFragment)
            usedKinds.add(kind)
        }
    }

    val elementPattern: ElementPattern<PsiElement>
        get() = PlatformPatterns.psiElement()
            .withLanguage(RsLanguage)
            .inside(psiElement<RsMacroArgument>())
}
