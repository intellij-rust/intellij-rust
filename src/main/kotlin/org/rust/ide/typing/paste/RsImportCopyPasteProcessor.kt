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
import org.rust.ide.inspections.fixes.QualifyPathFix
import org.rust.ide.inspections.import.AutoImportFix
import org.rust.ide.settings.RsCodeInsightSettings
import org.rust.ide.utils.import.*
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.inference
import org.rust.openapiext.toPsiFile
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

/**
 * Path of a single named element within the specified crate.
 */
data class QualifiedItemPath(val crateRelativePath: String, val crateId: CratePersistentId) {
    fun matches(target: RsQualifiedNamedElement?): Boolean =
        target != null
            && crateRelativePath == target.crateRelativePath
            && crateId == target.containingCrate.id
}

/**
 * Represents the end offset of an element that is a candidate for import after paste.
 * The end offset is relative to the start of a range of elements that were copied.
 */
typealias RelativeEndOffset = Int

/**
 * Maps text ranges in a copy-pasted region to qualified paths that can be used to resolve proper imports.
 * The range offsets are relative to the start of the copy-pasted region
 */
data class ImportMap(private val offsetToFqnMap: Map<RelativeEndOffset, QualifiedItemPath>) {
    fun elementToFqn(element: PsiElement, importOffset: Int): QualifiedItemPath? {
        val relativeEndOffset = toRelativeEndOffset(element, importOffset)
        return offsetToFqnMap[relativeEndOffset]
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

        val elements = gatherElements(file, bounds.range)
        val importCtx = elements.firstOrNull { it is RsElement } as? RsElement ?: return

        val importOffset = bounds.range.startOffset

        val visitor = ImportingVisitor(importOffset, data.importMap)

        runWriteAction {
            for (element in elements) {
                element.accept(visitor)
            }
            // We need to import the candidates after visiting all elements, otherwise the relative offsets could be
            // invalidated after an import has been added
            for (candidate in visitor.importCandidates) {
                candidate.import(importCtx)
            }
            for ((element, importInfo) in visitor.qualifyCandidates) {
                QualifyPathFix.qualify(element, importInfo)
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

private class ImportingVisitor(private val importOffset: Int, private val importMap: ImportMap) : RsRecursiveVisitor() {
    private val importCandidatesInner: MutableList<ImportCandidate> = mutableListOf()
    private val qualifyCandidatesInner: MutableList<Pair<RsPath, ImportInfo>> = mutableListOf()

    val importCandidates: List<ImportCandidate> = importCandidatesInner
    val qualifyCandidates: List<Pair<RsPath, ImportInfo>> = qualifyCandidatesInner

    override fun visitPath(path: RsPath) {
        val ctx = AutoImportFix.findApplicableContext(path)
        handleImport(path, ctx)
        super.visitPath(path)
    }

    override fun visitMethodCall(methodCall: RsMethodCall) {
        val ctx = AutoImportFix.findApplicableContext(methodCall)
        handleImport(methodCall, ctx)
        super.visitMethodCall(methodCall)
    }

    override fun visitPatBinding(binding: RsPatBinding) {
        val ctx = AutoImportFix.findApplicableContext(binding)
        handleImport(binding, ctx)
        super.visitPatBinding(binding)
    }

    private fun handleImport(element: RsElement, ctx: AutoImportFix.Context?) {
        val importMapCandidate = importMap.elementToFqn(element, importOffset) ?: return

        // Try to import with the "Auto import" context
        val candidate = ctx.getCandidate(importMapCandidate)
        if (candidate != null) {
            importCandidatesInner.add(candidate)
            return
        }

        // If import was not successful, try to fully qualify the name
        if (element is RsPath) {
            val resolvedTargets = element.reference?.multiResolve() ?: return
            if (resolvedTargets.isEmpty()) {
                // No accessible path found, just fully qualify the path
                if (importMapCandidate.crateId == element.containingCrate.id) {
                    val importInfo = ImportInfo.LocalImportInfo("crate${importMapCandidate.crateRelativePath}")
                    qualifyCandidatesInner.add(element to importInfo)
                }
            } else {
                val resolvedTarget = resolvedTargets.singleOrNull() as? RsQualifiedNamedElement
                if (importMapCandidate.matches(resolvedTarget)) return

                // Path resolves to something else than the original item
                val otherCtx = AutoImportFix.findApplicableContext(element, ImportContext.Type.OTHER)
                val otherCandidate = otherCtx.getCandidate(importMapCandidate) ?: return
                qualifyCandidatesInner.add(element to otherCandidate.info)
            }
        }
    }
}

private fun AutoImportFix.Context?.getCandidate(originalItem: QualifiedItemPath): ImportCandidate? =
    this?.candidates?.find { originalItem.matches(it.item) }

/**
 * Records mapping between offsets (relative to copy/paste content range) and fully qualified names of resolved items
 * from paths and method calls.
 */
private fun createFqnMap(file: RsFile, range: TextRange): ImportMap {
    val elements = gatherElements(file, range)
    val fqnMap = hashMapOf<RelativeEndOffset, QualifiedItemPath>()

    val visitor = object : RsRecursiveVisitor() {
        override fun visitPath(path: RsPath) {
            super.visitPath(path)

            // We only want to record the start of the path that can be imported (e.g. `a` in `a::b::c`).
            if (path.qualifier != null) return

            val target = path.reference?.resolve() as? RsQualifiedNamedElement
            if (target != null) {
                storeMapping(path, target)
            }
        }

        override fun visitMethodCall(methodCall: RsMethodCall) {
            val methods = methodCall.inference?.getResolvedMethod(methodCall)
            val target = methods?.firstNotNullOfOrNull {
                it.source.implementedTrait?.element
            }

            if (target != null) {
                storeMapping(methodCall, target)
            }

            super.visitMethodCall(methodCall)
        }

        override fun visitPatBinding(binding: RsPatBinding) {
            val target = binding.reference.resolve() as? RsQualifiedNamedElement
            if (target != null) {
                storeMapping(binding, target)
            }
            super.visitPatBinding(binding)
        }

        fun storeMapping(element: RsElement, target: RsQualifiedNamedElement) {
            fqnMap[toRelativeEndOffset(element, range.startOffset)] = QualifiedItemPath(
                target.crateRelativePath ?: return,
                target.containingCrate.id ?: return
            )
        }
    }
    for (element in elements) {
        element.accept(visitor)
    }

    return ImportMap(fqnMap)
}

private fun gatherElements(file: RsFile, range: TextRange): List<PsiElement> =
    CollectHighlightsUtil.getElementsInRange(file, range.startOffset, range.endOffset)
        .filter { elem -> elem !is PsiFile }

/**
 * Converts an element to its relative end offset within some region.
 * The start offset of the region is passed in `importOffset`.
 */
private fun toRelativeEndOffset(element: PsiElement, importOffset: Int): RelativeEndOffset =
    element.endOffset - importOffset
