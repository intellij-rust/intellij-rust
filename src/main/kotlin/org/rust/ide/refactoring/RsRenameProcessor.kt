/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.RenameableFakePsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewUtil
import com.intellij.util.containers.MultiMap
import org.rust.lang.core.macros.findElementExpandedFrom
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.processLocalVariables
import org.rust.lang.core.resolve.ref.RsReferenceBase
import javax.swing.Icon

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

    override fun canProcessElement(element: PsiElement): Boolean =
        element is RsNamedElement || element is RsFakeMacroExpansionRenameablePsiElement

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
        if (element !is RsNamedFieldDecl) {
            for (usage in usages) {
                val field = usage.element?.ancestorOrSelf<RsStructLiteralField>(RsBlock::class.java) ?: continue
                when {
                    field.isShorthand -> {
                        val newPatField = psiFactory.createStructLiteralField(field.referenceName, newName)
                        field.replace(newPatField)
                    }
                    field.referenceName == newName && (field.expr as? RsPathExpr)?.path == usage.element -> {
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
        val semanticElement = if (element is RsFakeMacroExpansionRenameablePsiElement) {
            element.expandedElement
        } else {
            element
        }
        val rename = if (
            semanticElement is RsLifetime ||
            semanticElement is RsLifetimeParameter ||
            semanticElement is RsLabel ||
            semanticElement is RsLabelDecl
        ) {
            newName.ensureQuote()
        } else {
            newName.trimStart('\'')
        }

        allRenames[element] = rename
    }

    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement {
        val superElement = (element as? RsAbstractable)?.superItem ?: element
        return superElement.findFakeElementForRenameInMacroBody() ?: superElement
    }

    private fun PsiElement.findFakeElementForRenameInMacroBody(): PsiElement? {
        if (this is RsNameIdentifierOwner) {
            val identifier = nameIdentifier
            val sourceIdentifier = identifier?.findElementExpandedFrom()
            if (sourceIdentifier != null) {
                when (val sourceIdentifierParent = sourceIdentifier.parent) {
                    is RsNameIdentifierOwner -> if (sourceIdentifierParent.name == name) {
                        return RsFakeMacroExpansionRenameablePsiElement.AttrMacro(this, sourceIdentifierParent)
                    }
                    is RsMacroBodyIdent -> if (sourceIdentifierParent.referenceName == name) {
                        return RsFakeMacroExpansionRenameablePsiElement.BangMacro(this, sourceIdentifierParent)
                    }
                    is RsMacroBodyQuoteIdent -> if (sourceIdentifierParent.referenceName == name) {
                        return RsFakeMacroExpansionRenameablePsiElement.BangMacro(this, sourceIdentifierParent)
                    }
                    is RsPath -> if (sourceIdentifierParent.referenceName == name) {
                        return RsFakeMacroExpansionRenameablePsiElement.AttrPath(this, sourceIdentifier)
                    }
                }
            }
        }

        return null
    }

    override fun substituteElementToRename(element: PsiElement, editor: Editor, renameCallback: RenameCallback) =
        renameCallback.pass(substituteElementToRename(element, editor))

    override fun findReferences(element: PsiElement, searchScope: SearchScope, searchInCommentsAndStrings: Boolean): Collection<PsiReference> {
        val refinedElement = if (element is RsFakeMacroExpansionRenameablePsiElement) {
            element.expandedElement
        } else {
            element
        }
        return super.findReferences(refinedElement, searchScope, searchInCommentsAndStrings)
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        super.prepareRenaming(element, newName, allRenames)
        val semanticElement = if (element is RsFakeMacroExpansionRenameablePsiElement) {
            element.expandedElement
        } else {
            element
        }
        when (semanticElement) {
            is RsAbstractable -> {
                val trait = (semanticElement.owner as? RsAbstractableOwner.Trait)?.trait ?: return
                trait.searchForImplementations()
                    .mapNotNull { it.findCorrespondingElement(semanticElement) }
                    .forEach { allRenames[it.findFakeElementForRenameInMacroBody() ?: it] = newName }
            }
            is RsMod -> {
                if (semanticElement is RsFile && semanticElement.declaration == null) return
                if (semanticElement.pathAttribute != null) return

                val ownedDir = semanticElement.getOwnedDirectory() ?: return
                allRenames[ownedDir] = newName
            }
        }

    }

    private fun String.ensureQuote(): String = if (startsWith('\'')) this else "'$this"
}

private sealed class RsFakeMacroExpansionRenameablePsiElement(
    val expandedElement: RsNameIdentifierOwner,
    parent: PsiElement
) : RenameableFakePsiElement(parent), PsiNameIdentifierOwner {
    override fun getIcon(): Icon? = expandedElement.getIcon(0)
    override fun getName(): String? = expandedElement.name
    override fun getTypeName(): String = UsageViewUtil.getType(expandedElement)

    class AttrMacro(
        semantic: RsNameIdentifierOwner,
        val sourceElement: RsNameIdentifierOwner,
    ) : RsFakeMacroExpansionRenameablePsiElement(semantic, sourceElement.parent) {
        override fun getNameIdentifier(): PsiElement? = sourceElement.nameIdentifier
        override fun setName(name: String): PsiElement {
            sourceElement.setName(name)
            return this
        }
    }

    class BangMacro(
        semantic: RsNameIdentifierOwner,
        val sourceElement: RsReferenceElementBase,
    ) : RsFakeMacroExpansionRenameablePsiElement(semantic, sourceElement.parent) {
        override fun getNameIdentifier(): PsiElement? = sourceElement.referenceNameElement
        override fun setName(name: String): PsiElement {
            sourceElement.reference!!.handleElementRename(name)
            return this
        }
    }

    class AttrPath(
        semantic: RsNameIdentifierOwner,
        val sourceElement: PsiElement,
    ) : RsFakeMacroExpansionRenameablePsiElement(semantic, sourceElement.parent) {
        override fun getNameIdentifier(): PsiElement = sourceElement
        override fun setName(name: String): PsiElement {
            RsReferenceBase.doRename(sourceElement, name)
            return this
        }
    }
}
