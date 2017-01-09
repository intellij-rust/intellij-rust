package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.RustUseGlobElement
import org.rust.lang.core.psi.RustUseItemElement
import org.rust.lang.core.psi.impl.mixin.isSelf
import org.rust.lang.core.psi.util.parentOfType

/**
 * Removes the curly braces on singleton imports, changing from this
 *
 * ```
 * import std::{mem};
 * ```
 *
 * to this:
 *
 * ```
 * import std::mem;
 * ```
 */
class RemoveCurlyBracesIntention : RustElementBaseIntentionAction() {
    override fun getText() = "Remove curly braces"
    override fun getFamilyName() = text

    override fun invokeImpl(project: Project, editor: Editor, element: PsiElement) {
        // Get our hands on the various parts of the use item:
        val useItem = element.parentOfType<RustUseItemElement>() ?: return
        val path = useItem.path ?: return
        val globList = useItem.useGlobList ?: return
        val identifier = globList.children[0]

        // Save the cursor position, adjusting for curly brace removal
        val caret = editor.caretModel.offset
        val newOffset = when {
            caret < identifier.textOffset -> caret
            caret < identifier.textOffset + identifier.textLength -> caret - 1
            else -> caret - 2
        }

        // Conjure up a new use item to make a new path containing the
        // identifier we want; then grab the relevant parts
        val newUseItem = RustPsiFactory(project).createUseItem("dummy::${identifier.text}")
        val newPath = newUseItem.path ?: return
        val newSubPath = newPath.path ?: return
        val newAlias = newUseItem.alias

        // Attach the identifier to the old path, then splice that path into
        // the use item. Delete the old glob list and attach the alias, if any.
        newSubPath.replace(path.copy())
        path.replace(newPath)
        useItem.coloncolon?.delete()
        globList.delete()
        newAlias?.let { it -> useItem.addBefore(it, useItem.semicolon) }

        editor.caretModel.moveToOffset(newOffset)
    }

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        val useItem = element.parentOfType<RustUseItemElement>() ?: return false
        val list = useItem.useGlobList ?: return false
        if (list.children.size != 1) return false

        val listItem = list.children[0]
        if (listItem is RustUseGlobElement) {
            return !listItem.isSelf
        } else {
            return false
        }
    }
}
