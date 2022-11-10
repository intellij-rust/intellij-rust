/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.RsLanguage
import org.rust.lang.core.macros.*
import org.rust.lang.core.macros.MacroExpansionContext.EXPR
import org.rust.lang.core.macros.MacroExpansionContext.STMT
import org.rust.lang.core.macros.decl.FragmentKind
import org.rust.lang.core.macros.decl.FragmentKind.*
import org.rust.lang.core.macros.decl.MacroGraphWalker
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psiElement
import org.rust.openapiext.Testmark

/**
 * Provides completion inside a macro argument (e.g. `foo!(/*caret*/)`) if the macro is NOT expanded
 * successfully, i.e. [RsMacroCall.expansion] == null. If macro is expanded successfully,
 * [RsFullMacroArgumentCompletionProvider] is used.
 */
object RsPartialMacroArgumentCompletionProvider : RsCompletionProvider() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        fun addCompletions(fragment: PsiElement, offset: Int) {
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
        val macro = macroCall.resolveToMacro()
        val graph = macro?.graph
        val offsetInArgument = parameters.offset - bodyTextRange.startOffset

        Testmarks.Touched.hit()

        val fragmentDescriptors = if (graph != null) {
            MacroGraphWalker(project, graph, macroCallBody, offsetInArgument).run()
        } else {
            emptyList()
        }

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

        // The "last resort" completion - try to parse the entire macro body as a plain Rust code
        if (usedKinds.isEmpty()) {
            val codeFragment = RsPsiFactory(project, markGenerated = false, eventSystemEnabled = true)
                .createFile(macroCallBody)
            codeFragment.putUserData(FORCE_OUT_OF_SCOPE_COMPLETION, false)
            for (child in codeFragment.children) {
                if (child is RsExpandedElement) {
                    child.setContext(macroCall)
                }
            }
            addCompletions(codeFragment, offsetInArgument)
            return
        }
    }

    override val elementPattern: ElementPattern<PsiElement>
        get() = psiElement()
            .withLanguage(RsLanguage)
            .inside(psiElement<RsMacroArgument>())

    object Testmarks {
        object Touched : Testmark()
    }
}
