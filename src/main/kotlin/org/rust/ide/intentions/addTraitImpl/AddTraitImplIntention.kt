/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.addTraitImpl

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.inspections.fixes.addTypeArguments
import org.rust.ide.inspections.import.AutoImportFix
import org.rust.ide.intentions.RsCodeFragmentPopup
import org.rust.ide.intentions.RsElementBaseIntentionAction
import org.rust.ide.refactoring.implementMembers.generateTraitMembers
import org.rust.ide.utils.import.import
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.resolveToBoundTrait
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.asTy
import org.rust.lang.core.types.implLookup
import org.rust.openapiext.runWriteCommandAction

class AddTraitImplIntention : RsElementBaseIntentionAction<AddTraitImplIntention.Context>() {
    override fun getText() = "Implement trait"
    override fun getFamilyName() = text

    class Context(val type: RsStructOrEnumItemElement, val name: String)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val struct = element.ancestorStrict<RsStructOrEnumItemElement>() ?: return null
        val name = struct.name ?: return null
        return Context(struct, name)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val traitFragment = RsPathCodeFragment(project, PATH_FRAGMENT_TEXT, true, ctx.type,
            RustParserUtil.PathParsingMode.TYPE, setOf(Namespace.Types))

        if (isUnitTestMode) {
            addTraitImplementations(editor, ctx, traitFragment.path)
        } else {
            RsCodeFragmentPopup.show(editor, project, traitFragment, {
                addTraitImplementations(editor, ctx, traitFragment.path)
            }) {
                val trait = traitFragment.path?.reference?.resolve() as? RsTraitItem
                if (trait != null) {
                    null
                } else {
                    "Could not resolve `${traitFragment.text} to a trait`"
                }
            }
        }
    }

    override fun startInWriteAction(): Boolean = false
    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement = currentFile

    private fun addTraitImplementations(editor: Editor, ctx: Context, path: RsPath?) {
        val trait = path?.reference?.resolve() as? RsTraitItem ?: return
        val project = ctx.type.project
        val factory = RsPsiFactory(project)
        val traits = mutableListOf<Pair<RsTraitItem, RsTraitRef?>>()
        collectSuperTraits(trait, null, traits, mutableSetOf())

        val implLookup = ctx.type.implLookup
        val type = ctx.type.asTy()

        val impls = project.runWriteCommandAction {
            val impls = mutableListOf<RsImplItem>()
            for ((traitCandidate, traitRef) in traits) {
                val ref = traitRef?.resolveToBoundTrait() ?: BoundElement(traitCandidate)
                val canSelect = implLookup.canSelect(TraitRef(type, ref))
                if (!canSelect) {
                    val impl = implementTrait(factory, traitCandidate, ctx) ?: continue
                    impls.add(impl)
                    val traitPath = impl.traitRef?.path ?: continue
                    AutoImportFix.findApplicableContext(project, traitPath)?.candidates?.firstOrNull()?.import(impl)
                }
            }
            impls
        }
        impls.forEach {
            generateTraitMembers(it, editor)
        }
    }

    private fun implementTrait(
        factory: RsPsiFactory,
        trait: RsTraitItem,
        ctx: Context
    ): RsImplItem? {
        val traitName = trait.name ?: return null
        val impl = factory.createTraitImplItem(ctx.name, traitName, ctx.type.typeParameterList, ctx.type.whereClause)
        val insertedImpl = ctx.type.parent.addAfter(impl, ctx.type) as RsImplItem

        val traitParameters = trait.typeParameterList
        val traitRef = insertedImpl.traitRef
        if (traitRef != null && traitParameters != null) {
            if (ctx.type.typeParameterList == null) {
                insertedImpl.addAfter(traitParameters.copy(), insertedImpl.impl)
                addTypeArguments(traitRef)
            } else {
                traitRef.addAfter(factory.createTypeArgumentList(emptyList()), traitRef.path)
            }
        }

        return insertedImpl
    }

    companion object {
        @JvmField
        @VisibleForTesting
        var PATH_FRAGMENT_TEXT: String = ""
    }
}

private fun collectSuperTraits(
    trait: RsTraitItem,
    ref: RsTraitRef?,
    ordered: MutableList<Pair<RsTraitItem, RsTraitRef?>>,
    visited: MutableSet<RsTraitItem>
) {
    if (trait !in visited) {
        visited.add(trait)
        ordered.add(Pair(trait, ref))
    }

    trait.typeParamBounds?.polyboundList?.forEach {
        val superTrait = it.bound.traitRef?.path?.reference?.resolve() as? RsTraitItem ?: return@forEach
        collectSuperTraits(superTrait, it.bound.traitRef, ordered, visited)
    }
}
