package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
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
class RemoveCurlyBracesIntention : RsElementBaseIntentionAction<RemoveCurlyBracesIntention.Context>() {
    override fun getText() = "Remove curly braces"
    override fun getFamilyName() = text

    data class Context(
        val useItem: RsUseItem,
        val usePath: RsPath,
        val useGlobList: RsUseGlobList,
        val useGlobIdentifier: PsiElement
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RemoveCurlyBracesIntention.Context? {
        val useItem = element.parentOfType<RsUseItem>() ?: return null
        val useItemPath = useItem.path ?: return null
        val useGlobList = useItem.useGlobList ?: return null
        if (useGlobList.children.size != 1) return null

        val listItem = useGlobList.children[0]
        if (listItem !is RsUseGlob || listItem.isSelf) return null

        return Context(
            useItem,
            useItemPath,
            useGlobList,
            listItem
        )
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (useItem, path, globList, identifier) = ctx

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
        newAlias?.let { useItem.addBefore(it, useItem.semicolon) }

        editor.caretModel.moveToOffset(newOffset)
    }
}
