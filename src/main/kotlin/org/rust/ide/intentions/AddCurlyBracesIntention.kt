package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.RustPsiFactory
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
class AddCurlyBracesIntention : RustElementBaseIntentionAction<AddCurlyBracesIntention.Context>() {
    override fun getText() = "Add curly braces"
    override fun getFamilyName() = text

    class Context(
        val useItem: RsUseItem,
        val path: RsPath
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val useItem = element.parentOfType<RsUseItem>() ?: return null
        val path = useItem.path ?: return null
        if (useItem.useGlobList != null) return null
        return Context(useItem, path)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val identifier = ctx.path.referenceNameElement
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

        val alias = ctx.useItem.alias

        // If there was an alias before, insert it into the new glob item
        if (alias != null) {
            val newGlobItem = newGlobList.children[0]
            newGlobItem.addAfter(alias, newGlobItem.lastChild)
        }

        // Remove the identifier from the path by replacing it with its subpath
        ctx.path.replace(ctx.path.path ?: return)

        // Delete the alias of the identifier, if any
        alias?.delete()

        // Insert the double colon and glob list into the use item
        ctx.useItem.addBefore(newColonColon, ctx.useItem.semicolon)
        ctx.useItem.addBefore(newGlobList, ctx.useItem.semicolon)

        editor.caretModel.moveToOffset(newOffset)
    }
}
