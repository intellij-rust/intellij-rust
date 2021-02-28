/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapiext.Testmark
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.RsLanguage
import org.rust.lang.core.macros.MacroExpansionContext.EXPR
import org.rust.lang.core.macros.MacroExpansionContext.STMT
import org.rust.lang.core.macros.MacroExpansionMode
import org.rust.lang.core.macros.decl.FragmentKind
import org.rust.lang.core.macros.decl.FragmentKind.*
import org.rust.lang.core.macros.decl.MacroGraphWalker
import org.rust.lang.core.macros.expansionContext
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psiElement

/**
 * Provides completion inside a macro argument (e.g. `foo!(/*caret*/)`) if the macro is NOT expanded
 * successfully, i.e. [RsMacroCall.expansion] == null. If macro is expanded successfully,
 * [RsFullMacroArgumentCompletionProvider] is used.
 */
object RsPartialMacroArgumentCompletionProvider : RsCompletionProvider() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        fun addCompletions(fragment: RsCodeFragment, offset: Int) {
            val element = fragment.findElementAt(offset) ?: return
            rerunCompletion(parameters.withPosition(element, offset), result)
        }

        val project = parameters.originalFile.project
        val position = parameters.position
        val macroCall = position.ancestorStrict<RsMacroArgument>()?.ancestorStrict<RsMacroCall>() ?: return

        val condition = macroCall.expansion != null &&
            project.macroExpansionManager.macroExpansionMode is MacroExpansionMode.New &&
            macroCall.expansionContext !in listOf(EXPR, STMT) // TODO remove after hygiene merge
        if (condition) return

        val bodyTextRange = macroCall.bodyTextRange ?: return
        val macroCallBody = macroCall.macroBody ?: return
        val macro = macroCall.resolveToMacro() ?: return
        val graph = macro.graph ?: return
        val offsetInArgument = parameters.offset - bodyTextRange.startOffset

        Testmarks.touched.hit()

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

    override val elementPattern: ElementPattern<PsiElement>
        get() = psiElement()
            .withLanguage(RsLanguage)
            .inside(psiElement<RsMacroArgument>())

    object Testmarks {
        val touched = Testmark("RsPartialMacroArgumentCompletionProvider.touched")
    }
}
