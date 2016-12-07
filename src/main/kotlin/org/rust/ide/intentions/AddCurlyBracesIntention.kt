package org.rust.ide.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.RustUseItemElement
import org.rust.lang.core.psi.util.parentOfType

/**
 * Adds curly braces to singleton imports, changing from this
 *
 * ```
 * import std::mem;
 * ```
 *
 * to this:
 *
 * ```
 * import std::{mem};
 * ```
 */
class AddCurlyBracesIntention : PsiElementBaseIntentionAction() {
    override fun getText() = "Add curly braces"
    override fun getFamilyName() = text
    override fun startInWriteAction() = true

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        // Get our hands on the pieces:
        val useItem = element.parentOfType<RustUseItemElement>() ?: return
        val path = useItem.path ?: return
        val identifier = path.identifier ?: return
        val alias = useItem.alias

        // Remember the caret position, adjusting by the new curly braces
        val caret = editor.caretModel.offset
        val newOffset = when {
            caret < identifier.textOffset -> caret
            caret < identifier.textOffset + identifier.textLength -> caret + 1
            else -> caret + 2
        }

        // Create a new use item that contains a glob list that we can use.
        // Then extract from it the glob list and the double colon.
        val newUseItem = RustPsiFactory(project).createUseItem("dummy::{${identifier.text}}")
        val newGlobList = newUseItem.useGlobList ?: return
        val newColonColon = newUseItem.coloncolon ?: return

        // If there was an alias before, insert it into the new glob item
        alias?.let { it ->
            val newGlobItem = newGlobList.children[0]
            newGlobItem.addAfter(it, newGlobItem.lastChild)
        }

        // Remove the identifier from the path by replacing it with its subpath
        path.replace(path.path ?: return)
        // Delete the alias of the identifier, if any
        useItem.alias?.delete()
        // Insert the double colon and glob list into the use item
        useItem.addBefore(newColonColon, useItem.semicolon)
        useItem.addBefore(newGlobList, useItem.semicolon)

        editor.caretModel.moveToOffset(newOffset)
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (!element.isWritable) return false

        val useItem = element.parentOfType<RustUseItemElement>() ?: return false
        return useItem.useGlobList == null && useItem.path != null
    }
}
