/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.visibility

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.intentions.RsElementBaseIntentionAction
import org.rust.ide.intentions.util.macros.InvokeInside
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

abstract class ChangeVisibilityIntention : RsElementBaseIntentionAction<ChangeVisibilityIntention.Context>() {
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.change.item.visibility")

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    abstract val visibility: String
    abstract fun isApplicable(element: RsVisibilityOwner): Boolean

    data class Context(val element: RsVisibilityOwner)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val visibleElement = element.ancestorStrict<RsVisibilityOwner>() ?: return null
        if (!isValidVisibilityOwner(visibleElement)) return null
        if (!isValidPlace(visibleElement, element)) return null
        if (!isApplicable(visibleElement)) return null

        val name = (visibleElement as? RsNamedElement)?.name?.let { " `$it`" } ?: ""

        text = RsBundle.message("intention.name.make", name, visibility)

        return Context(visibleElement)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        if (ctx.element.isPublic) {
            makePrivate(ctx.element)
        } else {
            makePublic(ctx.element, crateRestricted = true)
        }
    }

    companion object {
        fun isValidVisibilityOwner(visible: RsVisibilityOwner): Boolean {
            if (visible is RsEnumVariant) return false
            if (visible.containingCrate.origin != PackageOrigin.WORKSPACE) return false

            val owner = (visible as? RsAbstractable)?.owner
            if (owner is RsAbstractableOwner.Trait || owner?.isTraitImpl == true) return false

            return true
        }

        fun makePrivate(element: RsVisibilityOwner) {
            element.vis?.delete()
        }

        fun findInsertionAnchor(element: RsVisibilityOwner): PsiElement? {
            var anchor = getAnchor(element)
            // Check if there are any elements between `pub` and type keyword like
            // `extern "C"`, `unsafe`, `const`, `async` etc.
            // If prevNonCommentSibling is an attribute or null, then
            // there are no extra words, listed above, and we can
            // freely insert `pub` element right before keyword element.
            // Otherwise, there are some of these words and we roll anchor back to
            // insert `pub` element before them.
            while (true) {
                val prevNonCommentSibling = anchor.getPrevNonCommentSibling()
                if (!(prevNonCommentSibling is RsOuterAttr || prevNonCommentSibling == null)) {
                    anchor = prevNonCommentSibling
                } else {
                    break
                }
            }
            return anchor
        }

        fun makePublic(element: RsVisibilityOwner, crateRestricted: Boolean) {
            val project = element.project
            val anchor = findInsertionAnchor(element)

            val factory = RsPsiFactory(project)
            val newVis = if (crateRestricted) factory.createPubCrateRestricted() else factory.createPub()

            val currentVis = element.vis
            if (currentVis != null) {
                currentVis.replace(newVis)
            } else {
                element.addBefore(newVis, anchor)

                if (crateRestricted) {
                    // External functions and struct fields are special case when inserting pub(crate)
                    // so we have to insert space after `pub(crate)` manually
                    if (anchor is RsExternAbi || anchor?.parent is RsNamedFieldDecl) {
                        element.addBefore(RsPsiFactory(project).createPubCrateRestricted().nextSibling, anchor)
                    }
                }
            }
        }
    }
}

private fun getAnchor(element: PsiElement): PsiElement? = when (element) {
    is RsNameIdentifierOwner -> element.nameIdentifier
    is RsTupleFieldDecl -> element.typeReference
    is RsUseItem -> element.use
    else -> null
}

/**
 * Returns true if `element` is in a valid place in `visibleElement` for running the intention.
 */
private fun isValidPlace(visibleElement: RsVisibilityOwner, element: PsiElement): Boolean {
    val anchor = when (visibleElement) {
        is RsConstant -> visibleElement.const
        is RsEnumItem -> visibleElement.enum
        is RsFieldDecl -> element
        is RsFunction -> visibleElement.fn
        is RsMacro2 -> visibleElement.macroKw
        is RsModDeclItem -> visibleElement.mod
        is RsModItem -> visibleElement.mod
        is RsStructItem -> visibleElement.struct
        is RsTraitAlias -> visibleElement.trait
        is RsTraitItem -> visibleElement.trait
        is RsTypeAlias -> visibleElement.typeKw
        is RsUseItem -> element
        else -> null
    } ?: return false

    return element == anchor || anchor.leftSiblings.any { it.isAncestorOf(element) }
}
