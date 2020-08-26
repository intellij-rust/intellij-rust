/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.import

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapiext.Testmark
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.inspections.import.AutoImportFix.Type.*
import org.rust.ide.utils.import.ImportCandidate
import org.rust.ide.utils.import.ImportCandidatesCollector
import org.rust.ide.utils.import.ImportContext
import org.rust.ide.utils.import.import
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.TYPES_N_VALUES
import org.rust.lang.core.types.infer.ResolvedPath
import org.rust.lang.core.types.inference
import org.rust.openapiext.runWriteCommandAction

class AutoImportFix(element: RsElement, private val type: Type) : LocalQuickFixOnPsiElement(element), HighPriorityAction {

    private var isConsumed: Boolean = false

    override fun getFamilyName(): String = NAME
    override fun getText(): String = familyName

    public override fun isAvailable(): Boolean = super.isAvailable() && !isConsumed

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        invoke(project)
    }

    fun invoke(project: Project) {
        val element = startElement as? RsElement ?: return
        val (_, candidates) = when (type) {
            GENERAL_PATH -> findApplicableContext(project, element as RsPath)
            ASSOC_ITEM_PATH -> findApplicableContextForAssocItemPath(project, element as RsPath)
            METHOD -> findApplicableContext(project, element as RsMethodCall)
        } ?: return

        if (candidates.size == 1) {
            project.runWriteCommandAction {
                candidates.first().import(element)
            }
        } else {
            DataManager.getInstance().dataContextFromFocusAsync.onSuccess {
                chooseItemAndImport(project, it, candidates, element)
            }
        }
        isConsumed = true
    }

    private fun chooseItemAndImport(
        project: Project,
        dataContext: DataContext,
        items: List<ImportCandidate>,
        context: RsElement
    ) {
        showItemsToImportChooser(project, dataContext, items) { selectedValue ->
            project.runWriteCommandAction {
                selectedValue.import(context)
            }
        }
    }

    companion object {

        const val NAME = "Import"

        fun findApplicableContext(project: Project, path: RsPath): Context? {
            if (path.reference == null) return null

            val basePath = path.basePath()
            if (!basePath.isUnresolved) return null

            if (path.ancestorStrict<RsUseSpeck>() != null) {
                // Don't try to import path in use item
                Testmarks.pathInUseItem.hit()
                return null
            }

            val isNameInScope = path.hasInScope(basePath.referenceName, TYPES_N_VALUES)
            if (isNameInScope) {
                // Don't import names that are already in scope but cannot be resolved
                // because namespace of psi element prevents correct name resolution.
                // It's possible for incorrect or incomplete code like "let map = HashMap"
                Testmarks.nameInScope.hit()
                return null
            }

            val superPath = path.rootPath()
            val candidates = ImportCandidatesCollector.getImportCandidates(
                ImportContext.from(project, path, false),
                basePath.referenceName,
                superPath.text
            ) {
                superPath != basePath || !(it.item is RsMod || it.item is RsModDeclItem || it.item.parent is RsMembers)
            }.toList()

            return Context(GENERAL_PATH, candidates)
        }

        fun findApplicableContext(project: Project, methodCall: RsMethodCall): Context? {
            val results = methodCall.inference?.getResolvedMethod(methodCall) ?: emptyList()
            if (results.isEmpty()) return Context(METHOD, emptyList())
            val candidates = ImportCandidatesCollector.getImportCandidates(project, methodCall, results)?.toList() ?: return null
            return Context(METHOD, candidates)
        }

        /** Import traits for type-related UFCS method calls and assoc items */
        fun findApplicableContextForAssocItemPath(project: Project, path: RsPath): Context? {
            val parent = path.parent as? RsPathExpr ?: return null

            val qualifierElement = path.qualifier?.reference?.resolve()
            if (qualifierElement is RsTraitItem) return null

            val resolved = path.inference?.getResolvedPath(parent) ?: return null
            val sources = resolved.map {
                if (it !is ResolvedPath.AssocItem) return null
                it.source
            }
            val candidates = ImportCandidatesCollector.getTraitImportCandidates(project, path, sources)?.toList() ?: return null
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
        val pathInUseItem = Testmark("pathInUseItem")
        val nameInScope = Testmark("nameInScope")
    }
}
