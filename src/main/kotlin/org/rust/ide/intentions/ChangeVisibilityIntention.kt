/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class ChangeVisibilityIntention : RsElementBaseIntentionAction<ChangeVisibilityIntention.Context>() {
    override fun getFamilyName(): String = "Change item visibility"

    data class Context(val element: RsVisibilityOwner)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val visibleElement = element.ancestorStrict<RsVisibilityOwner>() ?: return null
        if (!isAvailable(visibleElement)) return null

        val isVisible = visibleElement.isPublic
        val name = (visibleElement as? RsNamedElement)?.name?.let { " `$it`" } ?: ""
        val newVisibility = if (isVisible) "private" else "public"

        text = "Make$name $newVisibility"

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
        fun isAvailable(visible: RsVisibilityOwner): Boolean =
            visible !is RsEnumVariant
                && visible.parent.parent !is RsTraitItem
                && visible.containingCrate?.origin == PackageOrigin.WORKSPACE

        fun makePrivate(element: RsVisibilityOwner) {
            element.vis?.delete()
        }
        fun makePublic(element: RsVisibilityOwner, crateRestricted: Boolean) {
            val project = element.project

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

            val factory = RsPsiFactory(project)
            val newVis = if (crateRestricted) factory.createPubCrateRestricted() else factory.createPub()

            val currentVis = element.vis
            if (currentVis != null) {
                currentVis.replace(newVis)
            }
            else {
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

private fun getAnchor(element: PsiElement): PsiElement? {
    return when (element) {
        is RsNameIdentifierOwner -> element.nameIdentifier
        is RsTupleFieldDecl -> element.typeReference
        is RsUseItem -> element.use
        else -> null
    }
}
