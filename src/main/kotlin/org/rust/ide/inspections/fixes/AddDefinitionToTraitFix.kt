/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.refactoring.extractTrait.makeAbstract
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class AddDefinitionToTraitFix(member: RsAbstractable) : LocalQuickFixAndIntentionActionOnPsiElement(member) {

    override fun getText() = "Add definition to trait"
    override fun getFamilyName(): String = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val member = startElement as? RsAbstractable ?: return
        val impl = member.parent?.parent as? RsImplItem ?: return
        val traitRef = impl.traitRef ?: return
        val trait = traitRef.resolveToTrait()?.findPreviewCopyIfNeeded(file) ?: return

        val factory = RsPsiFactory(project)
        val newMember = member.copy().makeAbstract(factory) as RsAbstractable
        val traitMembers = trait.members ?: return

        val anchor = findAnchor(impl, trait, member)
            ?: traitMembers.rbrace?.prevSibling
            ?: return
        traitMembers.addAfter(newMember, anchor)
        if (anchor == traitMembers.lbrace) {
            traitMembers.addAfter(factory.createNewline(), traitMembers.lbrace)
        }

        if (trait.containingMod != impl.containingMod) {
            RsImportHelper.importTypeReferencesFromElement(trait, member)
        }
    }

    // If trait members and impl members have the same order, we insert the new member
    // into the corresponding place. Otherwise, we add the new member to the end of the trait.
    private fun findAnchor(impl: RsImplItem, trait: RsTraitItem, member: RsAbstractable): RsAbstractable? {
        val traitMembers = trait.explicitMembers
        val mapping = impl.explicitMembers.associateWith { implMember ->
            traitMembers.find { traitMember ->
                implMember.elementType == traitMember.elementType && implMember.name == traitMember.name
            }
        }
        val areMembersInOrder = mapping.values.filterNotNull() == traitMembers
        if (!areMembersInOrder) return null

        return mapping.entries
            .takeWhile { (implMember, _) -> implMember != member }
            .mapNotNull { (_, traitMember) -> traitMember }
            .lastOrNull()
    }

    companion object {
        fun createIfCompatible(member: RsAbstractable): AddDefinitionToTraitFix? {
            val impl = member.parent?.parent as? RsImplItem ?: return null
            val traitRef = impl.traitRef ?: return null
            if (!checkTraitRef(traitRef)) return null
            if (!checkMember(member)) return null
            return AddDefinitionToTraitFix(member)
        }

        // `impl Trait<T> for ...` - OK
        // `impl Trait<i32> for ...` - not OK
        private fun checkTraitRef(traitRef: RsTraitRef): Boolean {
            val typeArgumentList = traitRef.path.typeArgumentList ?: return true
            val generics = typeArgumentList.stubChildrenOfType<RsElement>()
            return generics.all { generic ->
                generic is RsLifetime || generic is RsPathType && generic.path.isGeneric()
            }
        }

        // `type Foo = T` - OK, T will be removed
        // `fn foo() { ... T ... }` - OK, T will be removed
        // `fn foo(_: T) { ... }` - not OK
        // `const FOO: T = 0` - not OK
        private fun checkMember(member: RsAbstractable): Boolean {
            val elementsToCheck = when (member) {
                is RsTypeAlias -> return true
                is RsFunction -> member.valueParameters + listOfNotNull(member.retType)
                is RsConstant -> listOfNotNull(member.typeReference)
                else -> error("unreachable")
            }
            return elementsToCheck.all { element ->
                element.stubDescendantsOfTypeOrSelf<RsPath>().all { path ->
                    path.hasColonColon || !path.isGeneric()
                }
            }
        }

        private fun RsPath.isGeneric(): Boolean {
            val target = reference?.resolve() ?: return false
            return target is RsTypeParameter || target is RsConstParameter
        }
    }
}
