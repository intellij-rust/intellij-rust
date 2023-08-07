/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.Key
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.core.or
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.RAW_STRING_LITERAL
import org.rust.lang.core.psi.RsElementTypes.STRING_LITERAL
import org.rust.lang.core.psi.ext.macroName
import org.rust.lang.core.with

object RsBuildScriptCargoInstructionCompletionProvider : RsCompletionProvider() {
    override val elementPattern: ElementPattern<out PsiElement>
        get() = psiElement(STRING_LITERAL).or(psiElement(RAW_STRING_LITERAL))
            .with("buildScriptCargoInstruction") { e, ctx ->
                val literalExpr = e.parent as? RsLitExpr ?: return@with false
                val kind = literalExpr.kind as? RsLiteralKind.String ?: return@with false
                val arg = literalExpr.parent as? RsFormatMacroArg ?: return@with false
                val arguments = arg.parent as? RsFormatMacroArgument ?: return@with false
                if (arguments.formatMacroArgList.firstOrNull() != arg) return@with false
                val macroCall = arguments.parent as? RsMacroCall ?: return@with false
                val macroName = macroCall.macroName
                if (macroName != "print" && macroName != "println") return@with false
                if (!macroCall.containingCrate.kind.isCustomBuild) return@with false
                ctx?.put(CARGO_INSTRUCTION_KEY, kind)
                true
            }

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val valueOffsets = context[CARGO_INSTRUCTION_KEY]?.offsets?.value ?: return
        val position = parameters.position
        val currentText = position.text.substring(valueOffsets.startOffset, parameters.offset - position.textRange.startOffset)

        if (currentText.startsWith(CARGO_INSTRUCTION_PREFIX)) {
            val lookupElements = CARGO_INSTRUCTIONS.map {
                LookupElementBuilder.create(it)
                    .withInsertHandler { ctx, _ ->
                        if (!ctx.nextCharIs('=')) {
                            ctx.addSuffix("=")
                        }
                    }
            }
            result.withPrefixMatcher(currentText.substringAfter(CARGO_INSTRUCTION_PREFIX))
                .addAllElements(lookupElements)
        } else {
            result.addElement(
                LookupElementBuilder.create(CARGO_INSTRUCTION_PREFIX)
                    .withInsertHandler { ctx, _ ->
                        AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
                    }
            )
        }
    }

    private val CARGO_INSTRUCTION_KEY: Key<RsLiteralKind.String> = Key.create("CARGO_INSTRUCTION")

    private const val CARGO_INSTRUCTION_PREFIX = "cargo:"

    private val CARGO_INSTRUCTIONS = listOf(
        "rerun-if-changed",
        "rerun-if-env-changed",
        "rustc-link-arg",
        "rustc-link-arg-bin",
        "rustc-link-arg-bins",
        "rustc-link-arg-tests",
        "rustc-link-arg-examples",
        "rustc-link-arg-benches",
        "rustc-link-lib",
        "rustc-link-search",
        "rustc-flags",
        "rustc-cfg",
        "rustc-env",
        "rustc-cdylib-link-arg",
        "warning"
    )
}
