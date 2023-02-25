/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.*
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.annotator.format.RsFormatMacroAnnotator
import org.rust.lang.core.crate.asNotFake
import org.rust.lang.core.macros.*
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.RsAttrProcMacroOwner
import org.rust.stdext.removeLast

class RsMacroExpansionHighlightingPassFactory(
    val project: Project,
    registrar: TextEditorHighlightingPassRegistrar
) : DirtyScopeTrackingHighlightingPassFactory {
    private val myPassId: Int = registrar.registerTextEditorHighlightingPass(
        this,
        null,
        null,
        false,
        -1
    )

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        if (project.macroExpansionManager.macroExpansionMode !is MacroExpansionMode.New) return null
        if (!MACRO_HIGHLIGHTING_ENABLED_KEY.asBoolean()) return null

        val restrictedRange = FileStatusMap.getDirtyTextRange(editor, passId) ?: return null
        return RsMacroExpansionHighlightingPass(file, restrictedRange, editor.document)
    }

    override fun getPassId(): Int = myPassId

    companion object {
        @JvmStatic
        private val MACRO_HIGHLIGHTING_ENABLED_KEY = Registry.get("org.rust.lang.highlight.macro.body")
    }
}

class RsMacroExpansionHighlightingPass(
    private val file: PsiFile,
    private val restrictedRange: TextRange,
    document: Document
) : TextEditorHighlightingPass(file.project, document) {
    private val results = mutableListOf<HighlightInfo>()

    private fun createAnnotators(): Pair<List<Annotator>, List<Annotator>> {
        val annotatorsForDeclMacros = listOf(
            RsEdition2018KeywordsAnnotator(),
            RsAttrHighlightingAnnotator(),
            RsHighlightingMutableAnnotator(),
            RsCfgDisabledCodeAnnotator(),
            RsFormatMacroAnnotator(),
        )
        val annotatorsForAttrMacros = annotatorsForDeclMacros + listOf(
            RsErrorAnnotator(),
            RsUnsafeExpressionAnnotator(),
        )
        return annotatorsForDeclMacros to annotatorsForAttrMacros
    }

    @Suppress("UnstableApiUsage")
    override fun doCollectInformation(progress: ProgressIndicator) {
        val macros = mutableListOf<MacroCallPreparedForHighlighting>()

        PsiTreeUtil.processElements(file) {
            when (it) {
                is RsMacroCall -> {
                    if (it.macroArgument?.textRange?.intersects(restrictedRange) != true) return@processElements true
                    macros += it.prepareForExpansionHighlighting() ?: return@processElements true
                }
                is RsAttrProcMacroOwner -> {
                    if (it.textRange?.intersects(restrictedRange) != true) return@processElements true
                    macros += it.procMacroAttribute.attr?.prepareForExpansionHighlighting() ?: return@processElements true
                }
            }
            true // Continue
        }

        if (macros.isEmpty()) return

        val crate = (file as? RsFile)?.crate?.asNotFake
        val (annotatorsForDeclMacros, annotatorsForAttrMacros) = createAnnotators()

        while (macros.isNotEmpty()) {
            val macro = macros.removeLast()
            val annotationSession = AnnotationSession(macro.expansion.file)
                .apply { setCurrentCrate(crate) }

            @Suppress("DEPRECATION")
            val holder = AnnotationHolderImpl(annotationSession, false)
            val annotators = if (macro.isDeeplyAttrMacro) annotatorsForAttrMacros else annotatorsForDeclMacros

            for (element in macro.elementsForHighlighting) {
                for (ann in annotators) {
                    ProgressManager.checkCanceled()
                    holder.runAnnotatorWithContext(element, ann)
                }

                if (element is RsMacroCall) {
                    macros += element.prepareForExpansionHighlighting(macro) ?: continue
                } else if (element is RsAttrProcMacroOwner) {
                    macros += element.procMacroAttribute.attr?.prepareForExpansionHighlighting(macro) ?: continue
                }
            }

            for (ann in holder) {
                mapAndCollectAnnotation(macro, ann)
            }
        }
    }

    private fun mapAndCollectAnnotation(macro: MacroCallPreparedForHighlighting, ann: Annotation) {
        val originRange = TextRange(ann.startOffset, ann.endOffset)
        val originInfo = HighlightInfo.fromAnnotation(ann)
        val mappedRanges = mapRangeFromExpansionToCallBody(macro.expansion, macro.macroCall, originRange)
        for (mappedRange in mappedRanges) {
            results += originInfo.copyWithRange(mappedRange)
        }
    }

    override fun doApplyInformationToEditor() {
        UpdateHighlightersUtil.setHighlightersToEditor(
            myProject,
            myDocument,
            restrictedRange.startOffset,
            restrictedRange.endOffset,
            results,
            colorsScheme,
            id
        )
    }
}

private fun HighlightInfo.copyWithRange(newRange: TextRange): HighlightInfo {
    val forcedTextAttributesKey = forcedTextAttributesKey
    val forcedTextAttributes = forcedTextAttributes
    val description = description
    val toolTip = toolTip

    val b = HighlightInfo.newHighlightInfo(type)
        .range(newRange)
        .severity(severity)

    if (forcedTextAttributesKey != null) {
        b.textAttributes(forcedTextAttributesKey)
    } else if (forcedTextAttributes != null) {
        b.textAttributes(forcedTextAttributes)
    }

    if (description != null) {
        b.description(description)
    }
    if (toolTip != null) {
        b.escapedToolTip(toolTip)
    }
    if (isAfterEndOfLine) {
        b.endOfLine()
    }

    return b.createUnconditionally()
}
