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
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class MakePublicFix(
    element: RsVisible,
    elementName: String?,
    private val withinOneCrate: Boolean
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {

    private val _text = "Make `$elementName` public"

    override fun getFamilyName(): String = "Make public"

    override fun getText(): String = _text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        var anchor = getAnchor(startElement)
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
        if (!withinOneCrate) {
            startElement.addBefore(RsPsiFactory(project).createPub(), anchor)
        } else {
            startElement.addBefore(RsPsiFactory(project).createPubCrateRestricted(), anchor)
            // External functions and struct fields are special case when inserting pub(crate)
            // so we have to insert space after `pub(crate)` manually
            if (anchor is RsExternAbi || anchor?.parent is RsNamedFieldDecl) {
                startElement.addBefore(RsPsiFactory(project).createPubCrateRestricted().nextSibling, anchor)
            }
        }
    }

    private fun getAnchor(element: PsiElement): PsiElement? {
        return when (element) {
            is RsNameIdentifierOwner -> element.nameIdentifier
            is RsTupleFieldDecl -> element.typeReference
            else -> null
        }
    }

    companion object {
        fun createIfCompatible(visible: RsVisible, elementName: String?, withinOneCrate: Boolean): MakePublicFix? {
            return when {
                // TODO: Allow this fix for pub-restricted elements too
                visible.visibility is RsVisibility.Private
                    && visible !is RsEnumVariant
                    && visible.parent.parent !is RsTraitItem
                    && visible.containingCrate?.origin == PackageOrigin.WORKSPACE
                -> MakePublicFix(visible, elementName, withinOneCrate)
                else -> null
            }
        }
    }
}
