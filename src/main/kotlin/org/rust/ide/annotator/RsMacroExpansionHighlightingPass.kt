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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.RsBundle
import org.rust.ide.annotator.format.RsFormatMacroAnnotator
import org.rust.ide.colors.RsColor
import org.rust.ide.fixes.RsQuickFixBase
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.asNotFake
import org.rust.lang.core.macros.*
import org.rust.lang.core.psi.AttrCache
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.RsAttrProcMacroOwner
import org.rust.lang.core.psi.ext.isEnabledByCfg
import org.rust.openapiext.isUnitTestMode
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
                    macros += it.procMacroAttribute?.attr?.prepareForExpansionHighlighting() ?: return@processElements true
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
            val cfgDisabledElements = mutableListOf<PsiElement>()

            for (element in macro.elementsForHighlighting) {
                if (RsCfgDisabledCodeAnnotator.shouldHighlightAsCfsDisabled(element, holder)) {
                    cfgDisabledElements += element
                }
                for (ann in annotators) {
                    ProgressManager.checkCanceled()
                    holder.runAnnotatorWithContext(element, ann)
                }

                if (element is RsMacroCall) {
                    macros += element.prepareForExpansionHighlighting(macro) ?: continue
                } else if (element is RsAttrProcMacroOwner) {
                    macros += element.procMacroAttribute?.attr?.prepareForExpansionHighlighting(macro) ?: continue
                }
            }

            for (ann in holder) {
                mapAndCollectAnnotation(macro, ann)
            }

            if (crate != null && AnnotatorBase.isEnabled(RsCfgDisabledCodeAnnotator::class.java)) {
                highlightCfgDisabledRanges(crate, macro, cfgDisabledElements)
            }
        }
    }

    private fun mapAndCollectAnnotation(macro: MacroCallPreparedForHighlighting, ann: Annotation) {
        val originRange = TextRange(ann.startOffset, ann.endOffset)
        val originInfo = HighlightInfo.fromAnnotation(ann)
        val mappedRanges = mapRangeFromExpansionToCallBody(macro.expansion, macro.macroCall, originRange)
        for (mappedRange in mappedRanges) {
            val newInfo = originInfo.copyWithRange(mappedRange)
            originInfo.findRegisteredQuickFix<Any> { descriptor, quickfixTextRange ->
                val mappedQfRanges = mapRangeFromExpansionToCallBody(macro.expansion, macro.macroCall, quickfixTextRange)
                for (mappedQfRange in mappedQfRanges) {
                    if (descriptor.action !is RsQuickFixBase<*>) continue
                    newInfo.registerFix(descriptor.action, emptyList(), descriptor.displayName, mappedQfRange, null)
                }
                null
            }
            results += newInfo.createUnconditionally()
        }
    }

    private fun highlightCfgDisabledRanges(
        crate: Crate,
        macro: MacroCallPreparedForHighlighting,
        cfgDisabledElements: List<PsiElement>
    ) {
        val cache = AttrCache.HashMapCache(crate)
        val cfgDisabledMappedRanges = cfgDisabledElements.flatMapTo(HashSet()) {
            mapRangeFromExpansionToCallBody(macro.expansion, macro.macroCall, it.textRange)
        }.filter { range ->
            val element = file.findElementAt(range.startOffset) ?: return@filter false
            val expansionElements = element.findExpansionElements(cache) ?: return@filter false
            !expansionElements.any { it.isEnabledByCfg(crate) }
        }

        for (mappedRange in cfgDisabledMappedRanges) {
            val color = RsColor.CFG_DISABLED_CODE
            val severity = if (isUnitTestMode) color.testSeverity else RsCfgDisabledCodeAnnotator.CONDITIONALLY_DISABLED_CODE_SEVERITY
            results += HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
                .severity(severity)
                .textAttributes(color.textAttributesKey)
                .range(mappedRange)
                .descriptionAndTooltip(RsBundle.message("text.conditionally.disabled.code"))
                .createUnconditionally()
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

private fun HighlightInfo.copyWithRange(newRange: TextRange): HighlightInfo.Builder {
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

    return b
}
