/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing.paste

import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil
import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.changesHandler.range
import org.rust.ide.inspections.import.AutoImportFix
import org.rust.ide.settings.RsCodeInsightSettings
import org.rust.ide.utils.import.ImportCandidate
import org.rust.ide.utils.import.import
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsQualifiedNamedElement
import org.rust.lang.core.psi.ext.qualifiedName
import org.rust.lang.core.psi.ext.startOffset
import org.rust.lang.core.types.inference
import org.rust.openapiext.toPsiFile
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

data class ImportMap(private val offsetToFqnMap: Map<Int, String>) {
    fun elementToFqn(element: PsiElement, range: TextRange): String? {
        val offset = toRelativeOffset(element, range)
        return offsetToFqnMap[offset]
    }
}

class RsTextBlockTransferableData(val importMap: ImportMap) : TextBlockTransferableData {
    override fun getFlavor(): DataFlavor? = RsImportCopyPasteProcessor.dataFlavor

    override fun getOffsetCount(): Int = 0

    override fun getOffsets(offsets: IntArray?, index: Int): Int = index
    override fun setOffsets(offsets: IntArray?, index: Int): Int = index
}

class RsImportCopyPasteProcessor : CopyPastePostProcessor<RsTextBlockTransferableData>() {
    override fun collectTransferableData(
        file: PsiFile,
        editor: Editor,
        startOffsets: IntArray,
        endOffsets: IntArray
    ): List<RsTextBlockTransferableData> {
        if (file !is RsFile || DumbService.getInstance(file.getProject()).isDumb) return emptyList()
        if (!RsCodeInsightSettings.getInstance().importOnPaste) return emptyList()

        val startOffset = startOffsets.singleOrNull() ?: return emptyList()
        val endOffset = endOffsets.singleOrNull() ?: return emptyList()
        val range = TextRange(startOffset, endOffset)

        // If the whole file is copied, it's not useful to add imports
        if (range == file.textRange) return emptyList()

        val map = createFqnMap(file, range)

        return listOf(RsTextBlockTransferableData(map))
    }

    override fun extractTransferableData(content: Transferable): List<RsTextBlockTransferableData> {
        try {
            val data = content.getTransferData(dataFlavor) as? RsTextBlockTransferableData ?: return emptyList()
            return listOf(data)
        } catch (e: Throwable) {
            return emptyList()
        }
    }

    override fun processTransferableData(
        project: Project,
        editor: Editor,
        bounds: RangeMarker,
        caretOffset: Int,
        indented: Ref<in Boolean>,
        values: List<RsTextBlockTransferableData>
    ) {
        if (!RsCodeInsightSettings.getInstance().importOnPaste) return

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val data = values.getOrNull(0) ?: return
        val file = editor.document.toPsiFile(project) as? RsFile ?: return
        val range = bounds.range

        val elements = gatherElements(file, range)
        val importCtx = elements.firstOrNull { it is RsElement } as? RsElement ?: return

        val visitor = ImportingVisitor(range, data.importMap)

        runWriteAction {
            for (element in elements) {
                element.accept(visitor)
            }
            // We need to import the candidates after visiting all elements, otherwise the relative offsets could be
            // invalidated after an import has been added
            for (candidate in visitor.importCandidates) {
                candidate.import(importCtx)
            }
        }
    }

    companion object {
        val dataFlavor: DataFlavor? by lazy {
            try {
                val dataClass = RsReferenceData::class.java
                DataFlavor(
                    DataFlavor.javaJVMLocalObjectMimeType + ";class=" + dataClass.name,
                    "RsReferenceData",
                    dataClass.classLoader
                )
            } catch (e: NoClassDefFoundError) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

private class RsReferenceData

private class ImportingVisitor(private val range: TextRange, private val importMap: ImportMap) : RsRecursiveVisitor() {
    private val candidates: MutableList<ImportCandidate> = mutableListOf()

    val importCandidates: List<ImportCandidate> = candidates

    override fun visitPath(path: RsPath) {
        val ctx = AutoImportFix.findApplicableContext(path)
        handleContext(path, ctx)
        super.visitPath(path)
    }

    override fun visitMethodCall(methodCall: RsMethodCall) {
        val ctx = AutoImportFix.findApplicableContext(methodCall)
        handleContext(methodCall, ctx)
        super.visitMethodCall(methodCall)
    }

    override fun visitPatBinding(binding: RsPatBinding) {
        if (importMap.elementToFqn(binding, range) != null) {
            val ctx = AutoImportFix.findApplicableContext(binding)
            handleContext(binding, ctx)
        }
        super.visitPatBinding(binding)
    }

    private fun handleContext(element: PsiElement, ctx: AutoImportFix.Context?) {
        if (ctx != null) {
            val candidate = ctx.candidates.find {
                val fqn = importMap.elementToFqn(element, range)
                fqn == it.item.qualifiedName
            }
            if (candidate != null) {
                candidates.add(candidate)
            }
        }
    }
}

/**
 * Records mapping between offsets (relative to copy/paste content range) and fully qualified names of resolved items
 * from paths and method calls.
 */
private fun createFqnMap(file: RsFile, range: TextRange): ImportMap {
    val elements = gatherElements(file, range)
    val fqnMap = hashMapOf<Int, String>()

    val visitor = object : RsRecursiveVisitor() {
        override fun visitPath(path: RsPath) {
            val target = (path.reference?.resolve() as? RsQualifiedNamedElement)?.qualifiedName
            if (target != null) {
                fqnMap[toRelativeOffset(path, range)] = target
            }

            super.visitPath(path)
        }

        override fun visitMethodCall(methodCall: RsMethodCall) {
            val methods = methodCall.inference?.getResolvedMethod(methodCall)
            val target = methods?.firstNotNullOfOrNull {
                it.source.implementedTrait?.element?.qualifiedName
            }

            if (target != null) {
                fqnMap[toRelativeOffset(methodCall, range)] = target
            }

            super.visitMethodCall(methodCall)
        }

        override fun visitPatBinding(binding: RsPatBinding) {
            val target = (binding.reference.resolve() as? RsQualifiedNamedElement)?.qualifiedName
            if (target != null) {
                fqnMap[toRelativeOffset(binding, range)] = target
            }
            super.visitPatBinding(binding)
        }
    }
    for (element in elements) {
        element.accept(visitor)
    }

    return ImportMap(fqnMap)
}

private fun gatherElements(file: RsFile, range: TextRange): List<PsiElement> =
    CollectHighlightsUtil.getElementsInRange(file, range.startOffset, range.endOffset)

private fun toRelativeOffset(element: PsiElement, range: TextRange): Int = element.startOffset - range.startOffset
