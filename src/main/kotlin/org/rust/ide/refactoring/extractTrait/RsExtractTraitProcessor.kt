/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.extractTrait

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.BaseUsageViewDescriptor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.rust.ide.utils.GenericConstraints
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsExtractTraitProcessor(
    private val impl: RsImplItem,
    private val traitName: String,
    private val members: List<RsItemElement>,
) : BaseRefactoringProcessor(impl.project) {

    private val psiFactory = RsPsiFactory(impl.project)

    override fun getCommandName(): String = "Extract Trait"

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor =
        BaseUsageViewDescriptor(impl)

    override fun findUsages(): Array<UsageInfo> {
        return members.flatMap { member ->
            val references = ReferencesSearch.search(member, member.useScope)
            references.map { UsageInfo(it) }
        }.toTypedArray()
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val (traitImpl, trait) = createImpls() ?: return
        copyAttributesTo(traitImpl)
        copyAttributesTo(trait)
        moveMembersToCorrectImpls(traitImpl, trait)
        val insertedTrait = insertImpls(traitImpl, trait)
        addTraitImports(usages, insertedTrait)
    }

    private fun createImpls(): Pair<RsImplItem, RsTraitItem>? {
        val typeText = impl.typeReference?.text ?: return null
        val typesInsideMembers = members
            .filterIsInstance<RsFunction>()
            .flatMap { function ->
                listOfNotNull(function.retType, function.valueParameterList)
                    .flatMap { it.descendantsOfType<RsTypeReference>() }
            }
        val constraints = GenericConstraints.create(impl).filterByTypeReferences(typesInsideMembers)
        val genericsStruct = impl.typeParameterList?.text.orEmpty()
        val whereClauseStruct = impl.whereClause?.text.orEmpty()
        val genericsTrait = constraints.buildTypeParameters()
        val whereClauseTrait = constraints.buildWhereClause()

        val traitImpl = psiFactory.tryCreateImplItem(
            "impl $genericsStruct $traitName $genericsTrait for $typeText $whereClauseStruct { }"
        ) ?: return null
        val trait = psiFactory.tryCreateTraitItem(
            "trait $traitName $genericsTrait $whereClauseTrait { }"
        ) ?: return null
        return traitImpl to trait
    }

    private fun copyAttributesTo(target: RsTraitOrImpl) {
        for (attr in impl.outerAttrList) {
            if (attr.metaItem.name == "doc") continue
            val inserted = target.addAfter(attr, null)
            target.addAfter(psiFactory.createNewline(), inserted)
        }

        val members = target.members ?: return
        for (attr in impl.innerAttrList) {
            if (attr.metaItem.name == "doc") continue
            members.addAfter(attr, members.lbrace)
            members.addAfter(psiFactory.createNewline(), members.lbrace)
        }
    }

    private fun moveMembersToCorrectImpls(traitImpl: RsImplItem, trait: RsTraitItem) {
        members.forEach { (it as? RsVisibilityOwner)?.vis?.delete() }
        trait.members?.addMembers(members.map { it.copy().makeAbstract(psiFactory) }, psiFactory)

        traitImpl.members?.addMembers(members, psiFactory)
        members.forEach {
            (it.prevSibling as? PsiWhiteSpace)?.delete()
            it.delete()
        }
    }

    private fun insertImpls(traitImpl: RsImplItem, trait: RsTraitItem): RsTraitItem {
        val insertedTrait = impl.parent.addAfter(trait, impl) as RsTraitItem
        impl.parent.addAfter(traitImpl, impl)
        if (impl.members?.childrenOfType<RsItemElement>()?.isEmpty() == true) {
            impl.delete()
        }
        return insertedTrait
    }

    private fun addTraitImports(usages: Array<out UsageInfo>, trait: RsTraitItem) {
        val mods = usages.mapNotNullTo(hashSetOf()) {
            (it.element as? RsElement)?.containingMod
        }
        for (mod in mods) {
            val context = mod.childOfType<RsElement>() ?: continue
            RsImportHelper.importElement(context, trait)
        }
    }
}

private fun PsiElement.makeAbstract(psiFactory: RsPsiFactory): PsiElement {
    when (this) {
        is RsFunction -> {
            block?.delete()
            if (semicolon == null) add(psiFactory.createSemicolon())
        }
        is RsConstant -> {
            eq?.delete()
            expr?.delete()
        }
        is RsTypeAlias -> {
            eq?.delete()
            typeReference?.delete()
        }
    }
    return this
}

private fun RsMembers.addMembers(members: List<PsiElement>, psiFactory: RsPsiFactory) {
    val rbrace = rbrace ?: return
    addBefore(psiFactory.createNewline(), rbrace)
    for (member in members) {
        addBefore(member, rbrace)
        addBefore(psiFactory.createNewline(), rbrace)
    }
}
