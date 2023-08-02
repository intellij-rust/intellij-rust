/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.extractTrait

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.BaseUsageViewDescriptor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.rust.RsBundle
import org.rust.ide.utils.GenericConstraints
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.stdext.mapNotNullToSet

/**
 * This refactoring can be applied to either inherent impl or trait.
 *
 * ## `impl Struct`
 * - Create `trait NewTrait` and `impl NewTrait for Struct`
 * - Move members from `impl Struct` to `impl NewTrait for Struct`
 * - Find references to moved members and add trait imports
 *
 * `impl Struct { ... }`
 * ⇒
 * ```
 * trait NewTrait { ... }
 * impl NewTrait for Struct { ... }
 * ```
 *
 *
 * ## `trait Trait`
 * - Create `trait NewTrait`, add super `: NewTrait`
 * - For each `impl Trait for Struct`:
 *     - Create `impl NewTrait for Struct`
 *     - Move members from `impl Trait for Struct` to `impl NewTrait for Struct`
 *     - Find references to moved members and add trait imports
 *
 * ```
 * trait OldTrait { ... }
 * impl OldTrait for StructN { ... }
 * ```
 * ⇒
 * ```
 * trait NewTrait { ... }
 * impl NewTrait for StructN { ... }
 * ```
 */
class RsExtractTraitProcessor(
    private val traitOrImpl: RsTraitOrImpl,
    private val traitName: String,
    private val members: List<RsItemElement>,
) : BaseRefactoringProcessor(traitOrImpl.project) {

    private val psiFactory = RsPsiFactory(traitOrImpl.project)

    override fun getCommandName(): String = RsBundle.message("command.name.extract.trait")

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor =
        BaseUsageViewDescriptor(traitOrImpl, *usages.mapNotNull { it.element }.toTypedArray())

    private class MemberUsage(reference: PsiReference) : UsageInfo(reference)
    private class ImplUsage(val impl: RsImplItem) : UsageInfo(impl)

    override fun findUsages(): Array<UsageInfo> {
        val implUsages = run {
            if (traitOrImpl !is RsTraitItem) return@run emptyList()
            traitOrImpl.searchForImplementations().map { ImplUsage(it) }
        }
        val membersNames = members.mapNotNullToSet { it.name }
        val membersInImpls = implUsages.flatMap {
            it.impl.getMembersWithNames(membersNames)
        }

        val membersAll = members + membersInImpls
        val memberUsages = membersAll.flatMap { member ->
            val references = ReferencesSearch.search(member, member.useScope)
            references.map { MemberUsage(it) }
        }

        return (implUsages + memberUsages).toTypedArray()
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val newTraitConstraints = createNewTraitConstraints()
        val newTrait = createNewTrait(newTraitConstraints) ?: return
        if (traitOrImpl is RsTraitItem) {
            addDerivedBound(traitOrImpl)
        }

        addTraitImports(usages, newTrait)

        val impls = usages.filterIsInstance<ImplUsage>().map { it.impl } +
            listOfNotNull(traitOrImpl as? RsImplItem)
        for (impl in impls) {
            val newImpl = createNewImpl(impl, newTraitConstraints) ?: continue
            if (newImpl.containingMod != newTrait.containingMod) {
                RsImportHelper.importElement(newImpl, newTrait)
            }

            if (impl.traitRef == null && impl.explicitMembers.isEmpty()) {
                impl.delete()
            }
        }
    }

    private fun createNewTraitConstraints(): GenericConstraints {
        val typesInsideMembers = members
            .filterIsInstance<RsFunction>()
            .flatMap { function ->
                listOfNotNull(function.retType, function.valueParameterList)
                    .flatMap { it.descendantsOfType<RsTypeReference>() }
            }
        return GenericConstraints.create(traitOrImpl).filterByTypeReferences(typesInsideMembers)
    }

    private fun createNewTrait(constraints: GenericConstraints): RsTraitItem? {
        val typeParameters = constraints.buildTypeParameters()
        val whereClause = constraints.buildWhereClause()

        val members = when (traitOrImpl) {
            is RsImplItem -> members.map { it.copy().makeAbstract(psiFactory) }
            is RsTraitItem -> members.map { member -> member.copy().also { member.delete() } }
            else -> return null
        }
        val traitBody = members.joinToString(separator = "\n") { it.text }

        val visibility = getTraitVisibility()
        val trait = psiFactory.tryCreateTraitItem(
            "${visibility}trait $traitName $typeParameters $whereClause {\n$traitBody\n}"
        ) ?: return null

        copyAttributes(traitOrImpl, trait)
        return traitOrImpl.parent.addAfter(trait, traitOrImpl) as? RsTraitItem
    }

    /** [impl] - either an inherent impl or impl of existing derived trait. */
    private fun createNewImpl(impl: RsImplItem, traitConstraints: GenericConstraints): RsImplItem? {
        val typeText = impl.typeReference?.text ?: return null
        val typeParametersStruct = impl.typeParameterList?.text.orEmpty()
        val whereClauseStruct = impl.whereClause?.text.orEmpty()
        val typeArgumentsTrait = traitConstraints.buildTypeArguments()

        val newImplBody = extractMembersFromOldImpl(impl)
        val newImpl = psiFactory.tryCreateImplItem(
            "impl $typeParametersStruct $traitName $typeArgumentsTrait for $typeText $whereClauseStruct {\n$newImplBody\n}"
        ) ?: return null

        copyAttributes(traitOrImpl, newImpl)
        return impl.parent.addAfter(newImpl, impl) as? RsImplItem
    }

    private fun getTraitVisibility(): String {
        val unitedVisibility = when (traitOrImpl) {
            is RsTraitItem -> traitOrImpl.visibility
            is RsImplItem -> members.map { it.visibility }.reduce(RsVisibility::unite)
            else -> error("unreachable")
        }
        return unitedVisibility.format()
    }

    private fun extractMembersFromOldImpl(impl: RsImplItem): String {
        val membersNames = members.mapNotNullToSet { it.name }
        val membersToMove = impl.getMembersWithNames(membersNames)
        membersToMove.forEach {
            (it as? RsVisibilityOwner)?.vis?.delete()
            (it.prevSibling as? PsiWhiteSpace)?.delete()
            it.delete()
        }
        return membersToMove.joinToString("\n") { it.text }
    }

    private fun copyAttributes(source: RsTraitOrImpl, target: RsTraitOrImpl) {
        for (attr in source.outerAttrList) {
            if (attr.metaItem.name == "doc") continue
            val inserted = target.addAfter(attr, null)
            target.addAfter(psiFactory.createNewline(), inserted)
        }

        val members = target.members ?: return
        for (attr in source.innerAttrList) {
            if (attr.metaItem.name == "doc") continue
            members.addAfter(attr, members.lbrace)
            members.addAfter(psiFactory.createNewline(), members.lbrace)
        }
    }

    private fun addDerivedBound(trait: RsTraitItem) {
        val typeParamBounds = trait.typeParamBounds
        if (typeParamBounds == null) {
            val anchor = trait.identifier ?: trait.typeParameterList
            trait.addAfter(psiFactory.createTypeParamBounds(traitName), anchor)
        } else {
            typeParamBounds.addAfter(psiFactory.createPlus(), typeParamBounds.colon)
            typeParamBounds.addAfter(psiFactory.createPolybound(traitName), typeParamBounds.colon)
        }
    }

    private fun addTraitImports(usages: Array<out UsageInfo>, trait: RsTraitItem) {
        val mods = usages
            .filterIsInstance<MemberUsage>()
            .mapNotNullTo(hashSetOf()) {
                (it.element as? RsElement)?.containingMod
            }
        for (mod in mods) {
            val context = mod.childOfType<RsElement>() ?: continue
            RsImportHelper.importElement(context, trait)
        }
    }
}

private fun RsImplItem.getMembersWithNames(names: Set<String>): List<RsItemElement> {
    val implMembers = members?.childrenOfType<RsItemElement>() ?: return emptyList()
    return implMembers.filter {
        val name = it.name ?: return@filter false
        name in names
    }
}

fun PsiElement.makeAbstract(psiFactory: RsPsiFactory): PsiElement {
    if (this is RsVisibilityOwner) vis?.delete()
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
