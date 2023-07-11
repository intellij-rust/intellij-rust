/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.util.macros

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import com.intellij.psi.util.PsiTreeUtil
import org.rust.RsBundle
import org.rust.lang.core.macros.*
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.showErrorHint

object IntentionInMacroUtil {
    private val MUTABLE_EXPANSION_FILE_COPY = ThreadLocal<PsiFile>()

    fun unwrapEditor(editor: Editor): Editor {
        return if (editor is RsIntentionInsideMacroExpansionEditor) editor.originalEditor else editor
    }

    fun isMutableExpansionFile(file: PsiFile): Boolean {
        return MUTABLE_EXPANSION_FILE_COPY.get() == file
    }

    private inline fun <T> withMutableExpansionFile(file: PsiFile, action: () -> T): T {
        MUTABLE_EXPANSION_FILE_COPY.set(file)
        try {
            return action()
        } finally {
            MUTABLE_EXPANSION_FILE_COPY.remove()
        }
    }

    fun doActionAvailabilityCheckInsideMacroExpansion(
        originalEditor: Editor,
        originalFile: PsiFile,
        expandedElement: PsiElement,
        caretOffset: Int,
        action: (editorCopy: Editor) -> Boolean
    ): Boolean {
        val expansionFile = expandedElement.containingFile
        val fakeEditor = RsIntentionInsideMacroExpansionEditor(
            expansionFile,
            originalFile,
            originalEditor,
            caretOffset,
            null
        )
        return withMutableExpansionFile(expansionFile) { action(fakeEditor) }
    }

    fun <E: Editor?> runActionInsideMacroExpansionCopy(
        project: Project,
        originalEditor: E,
        originalFile: PsiFile,
        expandedElement: PsiElement,
        action: (editorCopy: E, expandedElementCopy: PsiElement) -> Boolean
    ) {
        val originalDoc = originalFile.viewProvider.document
        val macroCalls = expandedElement.macroCallExpandedFromSequence.toList()
        val originalDeepestMacroCall = macroCalls.first()
        val rootMacroCall = macroCalls.last()
        val deepestMacroCall = if (originalDeepestMacroCall == rootMacroCall
            && rootMacroCall.containingFile != originalFile
            && rootMacroCall.containingFile == originalFile.originalFile) {
            // Intention preview
            PsiTreeUtil.findSameElementInCopy(rootMacroCall, originalFile)
        } else {
            originalDeepestMacroCall
        }
        val expansion = originalDeepestMacroCall.expansion!!
        val rootMacroBodyRange = originalDoc.createRangeMarker(rootMacroCall.bodyTextRange ?: return)
        val deepestMacroCallContext = deepestMacroCall.contextToSetForExpansion as? RsElement ?: return
        val ranges = macroCalls
            .asReversed()
            .map { it.expansion!!.ranges }
            .reduce { acc, range -> acc.mapAll(range) }
            .let { MutableRangeMap(it.ranges.toMutableList()) }

        val psiFileCopy = expansion.file.copy() as RsFile
        psiFileCopy.setForcedReducedRangeMap(ranges)
        for (child in psiFileCopy.children) {
            if (child is RsExpandedElement) {
                child.setContext(deepestMacroCallContext)
                child.setExpandedFrom(deepestMacroCall)
            }
        }

        val documentCopy = psiFileCopy.viewProvider.document!!
        val context = RsIntentionInsideMacroExpansionContext(originalFile, documentCopy, ranges, rootMacroBodyRange)
        val editorCopy = if (originalEditor != null) {
            val initialMappedOffset = ranges.mapOffsetFromCallBodyToExpansion(originalEditor.caretModel.offset - rootMacroBodyRange.startOffset).singleOrNull()
            RsIntentionInsideMacroExpansionEditor(psiFileCopy, originalFile, originalEditor, initialMappedOffset, context)
        } else {
            null
        }

        if (originalDoc.isWritable) {
            val psiDocMgr = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase
            documentCopy.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    val causedByPsiChange = psiDocMgr.synchronizer.isInSynchronization(documentCopy)
                    val change = TextChange(TextRange(event.offset, event.offset + event.oldLength), event.newFragment)
                    val mappedChange = ranges.mapAndApplyChange(change)
                        ?.shiftRight(rootMacroBodyRange.startOffset)

                    if (!context.applyChangesToOriginalDoc || context.broken) return
                    if (mappedChange == null) {
                        context.broken = true
                        if (isUnitTestMode) {
                            error("The action tried to modify unmappable range inside a macro expansion; " +
                                "the action should not have been available in such context")
                        }
                        return
                    }
                    applyMappedChange(originalDoc, mappedChange)
                    if (causedByPsiChange) {
                        PsiDocumentManager.getInstance(project).commitDocument(originalDoc)
                        val changedRange = originalDoc.createRangeMarker(
                            mappedChange.range.startOffset,
                            mappedChange.range.startOffset + mappedChange.newText.length
                        )
                        context.changedRanges += changedRange
                    }
                }
            })
        }

        val expandedElementCopy = PsiTreeUtil.findSameElementInCopy(expandedElement, psiFileCopy)

        val actionResult = withMutableExpansionFile(psiFileCopy) {
            @Suppress("UNCHECKED_CAST")
            action(editorCopy as E, expandedElementCopy)
        }

        if (!actionResult) return

        if (context.broken) {
            if (originalFile.isIntentionPreviewElement) return
            // This message should not be shown to users really often since we do the best to filter out
            // unsuitable intentions during availability check
            originalEditor?.showErrorHint(
                RsBundle.message("hint.text.some.elements.that.action.going.to.change.exist.only.in.macro.expansion.so.cannot.be.changed.by.action")
            )
            return
        }

        if (ApplicationManager.getApplication().isWriteAccessAllowed || originalFile.isIntentionPreviewElement) {
            finishActionInMacroExpansionCopy(context, editorCopy)
        }
    }

    private fun MutableRangeMap.mapAndApplyChange(change: TextChange): TextChange? {
        val mappedRange = mapRangeOrOffset(this, change)
            ?.takeIf { it.length == change.range.length }

        textInExpansionReplaced(change.range, change.newText.length)
        return if (mappedRange == null) {
            null
        } else {
            TextChange(mappedRange, change.newText)
        }
    }

    private fun applyMappedChange(originalDoc: Document, change: TextChange) {
        originalDoc.replaceString(change.range.startOffset, change.range.endOffset, change.newText)
    }

    private fun mapRangeOrOffset(ranges: RangeMap, change: TextChange): TextRange? {
        return if (change.range.length == 0) {
            ranges.mapOffsetFromExpansionToCallBody(change.range.startOffset)
                ?.let { TextRange(it, it) }
        } else {
            ranges.mapTextRangeFromExpansionToCallBody(change.range)
                .singleOrNull()
                ?.srcRange
        }
    }

    fun finishActionInMacroExpansionCopy(editorCopy: Editor) {
        if (editorCopy is RsIntentionInsideMacroExpansionEditor) {
            finishActionInMacroExpansionCopy(editorCopy)
        }
    }

    fun finishActionInMacroExpansionCopy(editorCopy: RsIntentionInsideMacroExpansionEditor) {
        val mutableContext: RsIntentionInsideMacroExpansionContext = editorCopy.context ?: return
        finishActionInMacroExpansionCopy(mutableContext, editorCopy)
    }

    private fun finishActionInMacroExpansionCopy(
        mutableContext: RsIntentionInsideMacroExpansionContext,
        editorCopy: RsIntentionInsideMacroExpansionEditor?
    ) {
        if (mutableContext.finished || mutableContext.broken) {
            return
        }
        mutableContext.finished = true
        val originalFile = mutableContext.originalFile
        val project = originalFile.project
        val originalDoc = originalFile.viewProvider.document

        mutableContext.applyChangesToOriginalDoc = false
        val psiDocMgr = PsiDocumentManager.getInstance(project)
        psiDocMgr.doPostponedOperationsAndUnblockDocument(originalDoc)
        psiDocMgr.commitDocument(originalDoc)
        psiDocMgr.doPostponedOperationsAndUnblockDocument(mutableContext.documentCopy)
        psiDocMgr.commitDocument(mutableContext.documentCopy)
        mutableContext.applyChangesToOriginalDoc = true

        if (editorCopy != null) {
            val macroBodyStartOffset = mutableContext.rootMacroBodyRange.startOffset

            val initialMappedOffset = editorCopy.initialMappedOffset
            val editorCopyCaret = editorCopy.caretModel.currentCaret
            if (initialMappedOffset != null && initialMappedOffset != editorCopyCaret.offset) {
                val backMappedOffset = mutableContext.rangeMap.mapOffsetFromExpansionToCallBody(editorCopyCaret.offset)
                if (backMappedOffset != null) {
                    editorCopy.originalEditor.caretModel.moveToOffset(macroBodyStartOffset + backMappedOffset)
                }
            }

            if (editorCopyCaret.hasSelection()) {
                val selectionRange = TextRange(editorCopyCaret.selectionStart, editorCopyCaret.selectionEnd)
                val backMappedRange = mutableContext.rangeMap.mapTextRangeFromExpansionToCallBody(selectionRange)
                    .singleOrNull()
                    ?.srcRange
                    ?.shiftRight(macroBodyStartOffset)
                if (backMappedRange != null) {
                    editorCopy.originalEditor.caretModel.currentCaret.setSelection(
                        backMappedRange.startOffset,
                        backMappedRange.endOffset
                    )
                }
            }
        }

        if (mutableContext.changedRanges.isEmpty()) {
            return
        }

        markRangesToReformat(mutableContext.changedRanges, originalFile)
        mutableContext.changedRanges.clear()
    }

    private fun markRangesToReformat(mappedChanges: List<RangeMarker>, originalFile: PsiFile) {
        var someLeaf: PsiElement? = null
        for (rangeMarker in mappedChanges) {
            val range = rangeMarker.textRange
            rangeMarker.dispose()
            var leaf = originalFile.findElementAt(range.startOffset)
            if (leaf != null && leaf.startOffset == range.startOffset) {
                if (someLeaf !is PsiWhiteSpace){
                    someLeaf = leaf
                }
                val prevLeaf = PsiTreeUtil.prevLeaf(leaf)
                leaf = if (prevLeaf is PsiWhiteSpace) {
                    prevLeaf
                } else {
                    CodeEditUtil.markToReformatBefore(leaf.node, true)
                    PsiTreeUtil.nextLeaf(leaf)
                }
            }
            while (leaf != null && leaf.startOffset <= range.endOffset) {
                if (someLeaf !is PsiWhiteSpace){
                    someLeaf = leaf
                }
                CodeEditUtil.markToReformatBefore(leaf.node, false)
                CodeEditUtil.markToReformat(leaf.node, true)
                leaf = PsiTreeUtil.nextLeaf(leaf)
            }
        }

        // Make some PSI change to force the platform to actually reformat marked ranges in the file
        if (someLeaf != null) {
            if (someLeaf !is PsiWhiteSpace) {
                val nextLeaf = PsiTreeUtil.nextLeaf(someLeaf)
                if (nextLeaf is PsiWhiteSpace) {
                    someLeaf = nextLeaf
                }
            }
            if (someLeaf is PsiWhiteSpaceImpl) {
                val ws = PsiParserFacade.getInstance(originalFile.project).createWhiteSpaceFromText(someLeaf.text)
                CodeEditUtil.setNodeGenerated(ws.node, false)
                val prevLeaf = PsiTreeUtil.prevLeaf(someLeaf)
                val isMarkedToReformat = CodeEditUtil.isMarkedToReformat(someLeaf)
                val isMarkedToReformatBefore = CodeEditUtil.isMarkedToReformatBefore(someLeaf)
                someLeaf.replace(ws)
                if (prevLeaf != null) {
                    val recoveredWs = PsiTreeUtil.nextLeaf(prevLeaf)
                    if (recoveredWs != null) {
                        CodeEditUtil.setNodeGenerated(recoveredWs.node, false)
                        if (isMarkedToReformat) {
                            CodeEditUtil.markToReformat(recoveredWs.node, true)
                        }
                        if (isMarkedToReformatBefore) {
                            CodeEditUtil.markToReformatBefore(recoveredWs.node, true)
                        }
                    }
                }
            } else {
                val parent = someLeaf.parent
                val ws = PsiParserFacade.getInstance(originalFile.project).createWhiteSpaceFromText(" ")
                CodeEditUtil.setNodeGenerated(ws.node, false)
                parent.addAfter(ws, someLeaf)
                someLeaf.nextSibling.delete()
            }
        }
    }

    private data class TextChange(val range: TextRange, val newText: CharSequence) {
        fun shiftRight(delta: Int): TextChange = TextChange(range.shiftRight(delta), newText)
    }

    private class MutableRangeMap(ranges: MutableList<MappedTextRange>) : RangeMap(ranges) {
        private val rangesMut: MutableList<MappedTextRange> get() = super.ranges as MutableList<MappedTextRange>

        fun textInExpansionReplaced(changedRange: TextRange, newLength: Int) {
            val lengthDelta = newLength - changedRange.length
            val iter = rangesMut.listIterator()
            while (iter.hasNext()) {
                val existingMappedRange = iter.next()
                val existingRange = existingMappedRange.dstRange
                if (changedRange.startOffset < existingRange.endOffset) {
                    val replacement = if (changedRange.endOffset <= existingRange.startOffset) {
                        // `changedRange` lays before `existingRange` -> shift `existingRange` to `lengthDelta`
                        // `...[changedRange]...<existingRange>...`
                        existingMappedRange.shiftRight(lengthDelta)
                    } else if (existingRange.contains(changedRange)) {
                        // `...<existing[changedRange]Range>...`
                        val newLengthForRange = existingMappedRange.length + lengthDelta
                        if (newLengthForRange > 0) {
                            existingMappedRange.withLength(newLengthForRange)
                        } else {
                            null
                        }
                    }
                    else if (changedRange.contains(existingRange)) {
                        // `...[changed<existingRange>Range]...`
                        null
                    } else {
                        // TODO cut the range instead of removing it in these cases:
                        // `...<existing[Range>changedRange]...`
                        // `...[changedRange<existing]Range>...`
                        null
                    }
                    if (replacement != null) {
                        iter.set(replacement)
                    } else {
                        iter.remove()
                    }
                } else {
                    // Do nothing if the changed range is after the existing one
                    // `...<existingRange>[changedRange]...`
                }
            }
        }
    }
}
