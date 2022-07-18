/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.format

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.rust.ide.annotator.*
import org.rust.ide.injected.isDoctestInjection
import org.rust.lang.core.macros.MacroExpansionMode
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.existsAfterExpansion
import org.rust.openapiext.isUnitTestMode

class RsFormatMacroAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        val formatMacro = element as? RsMacroCall ?: return
        if (!formatMacro.existsAfterExpansion) return

        val (macroPos, macroArgs) = getFormatMacroCtx(formatMacro) ?: return

        val formatStr = macroArgs
            .getOrNull(macroPos)
            ?.expr as? RsLitExpr
            ?: return

        val parseCtx = parseParameters(formatStr) ?: return

        val errors = checkSyntaxErrors(parseCtx)
        for (error in errors) {
            holder.newAnnotation(HighlightSeverity.ERROR, error.error).range(error.range).create()
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

            holder.newAnnotation(HighlightSeverity.ERROR, annotation.error).range(annotation.range).create()
        }
    }
}
