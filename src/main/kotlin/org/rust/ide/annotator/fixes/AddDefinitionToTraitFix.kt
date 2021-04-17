/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.presentation.shortPresentableText
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.infer.resolve
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.regions.ReUnknown
import org.rust.lang.core.types.ty.Mutability
import org.rust.lang.core.types.type

class AddDefinitionToTraitFix(
    member: RsAbstractable,
) : LocalQuickFixAndIntentionActionOnPsiElement(member) {

    override fun getText() = "Add definition to trait"
    override fun getFamilyName(): String = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val member = (startElement as RsAbstractable)
        val impl = member.parent?.parent as RsImplItem? ?: return
        val traitRef = impl.traitRef
        val trait = traitRef?.resolveToBoundTrait() ?: return

        val newMember = RsPsiFactory(impl.project).createTraitMember(member, trait, impl)
        val traitMembers = trait.element.members!!

        // If trait members and impl members have the same order, we insert the new member
        // into the corresponding place. Otherwise we add the new member to the end of the trait.
        val existingMembersWithPosInImpl = trait.element.expandedMembers.map { existingMember ->
            existingMember to impl.expandedMembers.indexOfFirst {
                it.elementType == existingMember.elementType && it.name == existingMember.name
            }
        }
        val existingMembersOrder = existingMembersWithPosInImpl.map { it.second }
        val existingMembersInOrder = existingMembersOrder == existingMembersOrder.sorted()

        if (existingMembersInOrder) {
            val newMemberPosInImpl = impl.expandedMembers.indexOfFirst {
                it.elementType == newMember.elementType && it.name == newMember.name
            }

            val anchor = existingMembersWithPosInImpl.lastOrNull { it.second < newMemberPosInImpl }?.first
                ?: traitMembers.lbrace

            traitMembers.addAfter(newMember, anchor)
        } else {
            traitMembers.addBefore(newMember, traitMembers.rbrace)
        }

        RsImportHelper.importTypeReferencesFromElements(
            traitMembers,
            listOf(member),
            trait.subst,
            useAliases = true,
            skipUnchangedDefaultTypeArguments = true
        )
    }

    private fun RsPsiFactory.createTraitMember(
        implMember: RsAbstractable,
        trait: BoundElement<RsTraitItem>,
        impl: RsImplItem
    ): RsAbstractable {
        val subst = trait.subst
        return when (implMember) {
            is RsConstant -> createTraitConstantMember("const ${implMember.nameLikeElement.text}: ${implMember.typeReference?.substAndGetText(subst) ?: "_"};")
            is RsTypeAlias -> createTraitTypeAliasMember("type ${implMember.escapedName};")
            is RsFunction -> createTraitMethodMember("${implMember.getSignatureText(subst, trait, impl)};")
            else -> error("Unknown trait member")
        }
    }

    // TODO: This is duplicated from org.rust.ide.refactoring.implementMembers.impl
    private fun RsFunction.getSignatureText(
        subst: Substitution,
        trait: BoundElement<RsTraitItem>,
        impl: RsImplItem
    ): String? {
        val async = if (isAsync) "async " else ""
        val unsafe = if (isUnsafe) "unsafe " else ""
        // We can't simply take a substring of original method declaration
        // because of anonymous parameters.
        val name = escapedName ?: return null
        val generics = typeParameterList?.text ?: ""

        val selfArgument = listOfNotNull(selfParameter?.substAndGetText(subst))
        val typeParameters = trait.element.typeParameters.mapIndexed { index, typeParameter ->
            typeParameter.identifier.text to (index to typeParameter)
        }.toMap()
        val typeArguments = impl.traitRef?.path?.typeArguments.orEmpty()
        val valueArguments = valueParameters.map {
            val fnPointerType = when (val typeReference = it.typeReference) {
                is RsFnPointerType -> typeReference
                is RsBaseType -> {
                    val (index, typeParameter) = typeParameters[typeReference.type.shortPresentableText] ?: (-1 to null)
                    (typeArguments.getOrNull(index) ?: typeParameter?.typeReference) as? RsFnPointerType
                }
                else -> null
            }
            val extern = fnPointerType?.externAbi?.extern?.text?.let { text -> "$text " } ?: ""
            // fix possible anon parameter
            "${it.pat?.text ?: "_"}: $extern${it.typeReference?.substAndGetText(subst) ?: "()"}"
        }
        val allArguments = selfArgument + valueArguments

        val ret = retType?.typeReference?.substAndGetText(subst)?.let { "-> $it " } ?: ""
        val where = whereClause?.text ?: ""
        return "${async}${unsafe}fn $name$generics(${allArguments.joinToString(",")}) $ret$where"
    }

    // TODO: This is duplicated from org.rust.ide.refactoring.implementMembers.impl
    private fun RsSelfParameter.substAndGetText(subst: Substitution): String =
        if (isExplicitType) {
            buildString {
                append(self.text)
                append(colon!!.text)
                val type = typeReference?.substAndGetText(subst)
                append(type)
            }
        } else {
            buildString {
                append(and?.text ?: "")
                val region = lifetime.resolve().substitute(subst)
                if (region != ReUnknown) append("$region ")
                if (mutability == Mutability.MUTABLE) append("mut ")
                append(self.text)
            }
        }
}
