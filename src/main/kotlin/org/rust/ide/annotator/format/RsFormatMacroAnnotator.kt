/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.format

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.rust.ide.annotator.AnnotatorBase
import org.rust.ide.fixes.AddFormatStringFix
import org.rust.ide.injected.isDoctestInjection
import org.rust.lang.core.macros.MacroExpansionMode
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.existsAfterExpansion
import org.rust.lang.utils.addToHolder
import org.rust.openapiext.isUnitTestMode

class RsFormatMacroAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        val formatMacro = element as? RsMacroCall ?: return
        if (!formatMacro.existsAfterExpansion) return

        val resolvedMacro = formatMacro.path.reference?.resolve() as? RsMacro ?: return
        val (macroPos, macroArgs) = getFormatMacroCtx(formatMacro, resolvedMacro) ?: return

        val formatStr = macroArgs
            .getOrNull(macroPos)
            ?.expr as? RsLitExpr
        val parseCtx = formatStr?.let { parseParameters(it) }
        if (parseCtx == null) {
            annotateMissingFormatString(formatMacro, resolvedMacro, macroArgs, macroPos, holder)
            return
        }

        val errors = checkSyntaxErrors(parseCtx)
        for (error in errors) {
            addAnnotation(holder, error, formatMacro)
        }

        if (!holder.isBatchMode) {
            highlightParametersOutside(parseCtx, holder)
        }

        // skip advanced checks and highlighting if there are syntax errors
        if (errors.isNotEmpty()) {
            return
        }

        if (!holder.isBatchMode) {
            highlightParametersInside(parseCtx, holder)
        }

        val suppressTraitErrors = !isUnitTestMode &&
            (element.project.macroExpansionManager.macroExpansionMode !is MacroExpansionMode.New
                || element.isDoctestInjection)

        val parameters = buildParameters(parseCtx)
        val arguments = macroArgs
            .drop(macroPos + 1)
            .toList()
        val ctx = FormatContext(parameters, arguments, formatMacro)

        val annotations = checkParameters(ctx).toMutableList()
        annotations += checkArguments(ctx)

        for (annotation in annotations) {
            if (suppressTraitErrors && annotation.isTraitError) continue

            addAnnotation(holder, annotation, formatMacro)
        }
    }

    private fun addAnnotation(holder: AnnotationHolder, error: ErrorAnnotation, call: RsMacroCall) {
        if (error.diagnostic != null) {
            error.diagnostic.addToHolder(holder)
        } else {
            holder
                .newAnnotation(HighlightSeverity.ERROR, error.error)
                .range(error.range)
                .create()
        }
    }

    private fun annotateMissingFormatString(
        call: RsMacroCall,
        macro: RsMacro,
        macroArguments: List<RsFormatMacroArg>,
        formatStringPosition: Int,
        holder: AnnotationHolder
    ) {
        val macroName = macro.name ?: return
        if (macroName == "panic") return
        val formatString = macroArguments.getOrNull(formatStringPosition)
        val (message, range) = if (formatString != null) {
            if (formatString.expr is RsMacroExpr) return
            "Format argument must be a string literal" to formatString.textRange
        } else {
            if (macroName in listOf("println", "eprintln", "writeln")) return
            "Requires at least a format string argument" to call.textRange
        }
        val fix = AddFormatStringFix(call, formatStringPosition)
        holder
            .newAnnotation(HighlightSeverity.ERROR, message)
            .range(range)
            .withFix(fix)
            .create()
    }
}
