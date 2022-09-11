/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.import

import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.BatchQuickFix
import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.HintAction
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.inspections.import.AutoImportFix.Type.*
import org.rust.ide.settings.RsCodeInsightSettings
import org.rust.ide.utils.import.ImportCandidate
import org.rust.ide.utils.import.ImportCandidatesCollector
import org.rust.ide.utils.import.ImportContext
import org.rust.ide.utils.import.import
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.infer.ResolvedPath
import org.rust.lang.core.types.inference
import org.rust.openapiext.Testmark
import org.rust.openapiext.checkWriteAccessNotAllowed
import org.rust.openapiext.runWriteCommandAction

class AutoImportFix(element: RsElement, private val context: Context) :
    LocalQuickFixOnPsiElement(element), BatchQuickFix, PriorityAction, HintAction {

    private var isConsumed: Boolean = false

    override fun getFamilyName(): String = NAME
    override fun getText(): String = familyName

    public override fun isAvailable(): Boolean = super.isAvailable() && !isConsumed

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.TOP

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        invoke(project)
    }

    fun invoke(project: Project) {
        checkWriteAccessNotAllowed()
        val element = startElement as? RsElement ?: return
        val candidates = context.candidates
        if (candidates.size == 1) {
            project.runWriteCommandAction(text, "inspection.AutoImportFix") {
                candidates.first().import(element)
            }
        } else {
            DataManager.getInstance().dataContextFromFocusAsync.onSuccess {
                chooseItemAndImport(project, it, candidates, element)
            }
        }
        isConsumed = true
    }

    override fun applyFix(
        project: Project,
        descriptors: Array<CommonProblemDescriptor>,
        psiElementsToIgnore: List<PsiElement>,
        refreshViews: Runnable?
    ) {
        project.runWriteCommandAction(text, "inspection.AutoImportFix") {
            for (descriptor in descriptors) {
                val fix = descriptor.fixes?.filterIsInstance<AutoImportFix>()?.singleOrNull() ?: continue
                val candidate = fix.context.candidates.singleOrNull() ?: continue
                val context = fix.startElement as? RsElement ?: continue
                candidate.import(context)
            }
        }
        refreshViews?.run()
    }

    private fun chooseItemAndImport(
        project: Project,
        dataContext: DataContext,
        items: List<ImportCandidate>,
        context: RsElement
    ) {
        showItemsToImportChooser(project, dataContext, items) { selectedValue ->
            project.runWriteCommandAction(text, "inspection.AutoImportFix") {
                selectedValue.import(context)
            }
        }
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = isAvailable

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) = invoke(project)

    override fun startInWriteAction(): Boolean = false

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiFile = currentFile

    override fun showHint(editor: Editor): Boolean {
        if (!RsCodeInsightSettings.getInstance().showImportPopup) return false
        if (HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) return false

        val candidates = context.candidates
        val hint = candidates[0].info.usePath
        val multiple = candidates.size > 1
        val message = ShowAutoImportPass.getMessage(multiple, hint)
        val element = startElement
        HintManager.getInstance().showQuestionHint(editor, message, element.textOffset, element.endOffset) {
            invoke(element.project)
            true
        }
        return true
    }

    override fun fixSilently(editor: Editor): Boolean {
        if (!RsCodeInsightSettings.getInstance().addUnambiguousImportsOnTheFly) return false
        val candidates = context.candidates
        if (candidates.size != 1) return false
        val project = editor.project ?: return false
        invoke(project)
        return true
    }

    companion object {

        const val NAME = "Import"

        fun findApplicableContext(path: RsPath): Context? {
            if (path.reference == null) return null

            // `impl Future<Output=i32>`
            //              ~~~~~~ path
            val parent = path.parent
            if (parent is RsAssocTypeBinding && parent.eq != null && parent.path == path) return null

            val basePath = path.basePath()
            if (basePath.resolveStatus != PathResolveStatus.UNRESOLVED) return null

            if (path.ancestorStrict<RsUseSpeck>() != null) {
                // Don't try to import path in use item
                Testmarks.PathInUseItem.hit()
                return null
            }

            val referenceName = basePath.referenceName ?: return null
            val importContext = ImportContext.from(path, ImportContext.Type.AUTO_IMPORT) ?: return null
            val candidates = ImportCandidatesCollector.getImportCandidates(importContext, referenceName)

            return Context(GENERAL_PATH, candidates)
        }

        fun findApplicableContext(pat: RsPatBinding): Context? {
            val importContext = ImportContext.from(pat, ImportContext.Type.AUTO_IMPORT) ?: return null
            val candidates = ImportCandidatesCollector.getImportCandidates(importContext, pat.referenceName)
            if (candidates.isEmpty()) return null
            return Context(GENERAL_PATH, candidates)
        }

        fun findApplicableContext(methodCall: RsMethodCall): Context? {
            val results = methodCall.inference?.getResolvedMethod(methodCall) ?: emptyList()
            if (results.isEmpty()) return Context(METHOD, emptyList())
            val candidates = ImportCandidatesCollector.getImportCandidates(methodCall, results) ?: return null
            return Context(METHOD, candidates)
        }

        /** Import traits for type-related UFCS method calls and assoc items */
        fun findApplicableContextForAssocItemPath(path: RsPath): Context? {
            val parent = path.parent as? RsPathExpr ?: return null

            // `std::default::Default::default()`
            val qualifierElement = path.qualifier?.reference?.resolve()
            if (qualifierElement is RsTraitItem) return null

            // `<Foo as bar::Baz>::qux()`
            val typeQual = path.typeQual
            if (typeQual != null && typeQual.traitRef != null) return null

            val resolved = path.inference?.getResolvedPath(parent) ?: return null
            val sources = resolved.map {
                if (it !is ResolvedPath.AssocItem) return null
                it.source
            }
            val candidates = ImportCandidatesCollector.getTraitImportCandidates(path, sources) ?: return null
            return Context(ASSOC_ITEM_PATH, candidates)
        }
    }

    data class Context(
        val type: Type,
        val candidates: List<ImportCandidate>
    )

    enum class Type {
        GENERAL_PATH,
        ASSOC_ITEM_PATH,
        METHOD
    }

    object Testmarks {
        object PathInUseItem : Testmark()
    }
}
