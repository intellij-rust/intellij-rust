/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.processLocalVariables

class RsRenameProcessor : RenamePsiElementProcessor() {
    override fun createRenameDialog(
        project: Project,
        element: PsiElement,
        nameSuggestionContext: PsiElement?,
        editor: Editor?
    ): RenameDialog {
        return object : RenameDialog(project, element, nameSuggestionContext, editor) {
            override fun getFullName(): String {
                val mod = (element as? RsFile)?.modName ?: return super.getFullName()
                return "module $mod"
            }
        }
    }

    override fun canProcessElement(element: PsiElement): Boolean = element is RsNamedElement

    override fun findExistingNameConflicts(
        element: PsiElement,
        newName: String,
        conflicts: MultiMap<PsiElement, String>
    ) {
        val binding = element as? RsPatBinding ?: return
        val function = binding.parentOfType<RsFunction>() ?: return
        val functionName = function.name ?: return
        val foundConflicts = mutableListOf<String>()

        val scope = if (binding.parentOfType<RsValueParameter>() != null) {
            function.block?.rbrace?.getPrevNonCommentSibling() as? RsElement
        } else {
            binding
        }

        scope?.let { s ->
            processLocalVariables(s) {
                if (it.name == newName) {
                    val type = when (it.parent) {
                        is RsPatIdent -> {
                            if (it.parentOfType<RsValueParameter>() != null) {
                                "Parameter"
                            } else {
                                "Variable"
                            }
                        }
                        else -> "Binding"
                    }
                    foundConflicts.add("$type `$newName` is already declared in function `$functionName`")
                }
            }
        }

        if (foundConflicts.isNotEmpty()) {
            conflicts.put(element, foundConflicts)
        }
    }

    override fun renameElement(
        element: PsiElement,
        newName: String,
        usages: Array<out UsageInfo>,
        listener: RefactoringElementListener?
    ) {
        val psiFactory = RsPsiFactory(element.project)
        if (element is RsPatBinding) {
            usages.forEach {
                val field = it.element?.ancestorOrSelf<RsStructLiteralField>(RsBlock::class.java) ?: return@forEach
                when {
                    field.colon == null -> {
                        val newPatField = psiFactory.createStructLiteralField(element.text, newName)
                        field.replace(newPatField)
                    }
                    field.referenceName == newName && field.expr is RsPathExpr -> {
                        field.expr?.delete()
                        field.colon?.delete()
                    }
                }
            }
        }

        val newRenameElement = if (element is RsPatBinding && element.parent.parent is RsPatStruct) {
            val newPatField = psiFactory.createPatFieldFull(element.identifier.text, element.text)
            element.replace(newPatField).descendantOfTypeStrict<RsPatBinding>()!!
        } else element
        super.renameElement(newRenameElement, newName, usages, listener)
    }

    override fun prepareRenaming(
        element: PsiElement,
        newName: String,
        allRenames: MutableMap<PsiElement, String>,
        scope: SearchScope
    ) {
        if (element is RsLifetime || element is RsLifetimeParameter || element is RsLabel || element is RsLabelDecl) {
            allRenames.put(element, newName.ensureQuote())
        } else {
            allRenames.put(element, newName.trimStart('\''))
        }
    }

    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement =
        (element as? RsAbstractable)?.superItem ?: element

    override fun substituteElementToRename(element: PsiElement, editor: Editor, renameCallback: Pass<PsiElement>) =
        renameCallback.pass(substituteElementToRename(element, editor))

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        super.prepareRenaming(element, newName, allRenames)
        when (element) {
            is RsAbstractable -> {
                val trait = (element.owner as? RsAbstractableOwner.Trait)?.trait ?: return
                trait.searchForImplementations()
                    .mapNotNull { it.findCorrespondingElement(element) }
                    .forEach { allRenames[it] = newName }
            }
            is RsMod -> {
                if (element is RsFile && element.declaration == null) return
                if (element.pathAttribute != null) return

                val ownedDir = element.getOwnedDirectory() ?: return
                allRenames[ownedDir] = newName
            }
        }

    }

    private fun String.ensureQuote(): String = if (startsWith('\'')) this else "'$this"
}
