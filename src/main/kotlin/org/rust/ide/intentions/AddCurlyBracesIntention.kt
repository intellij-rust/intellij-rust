/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsUseSpeck
import org.rust.lang.core.psi.ext.isStarImport
import org.rust.lang.core.psi.ext.ancestorStrict

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
class AddCurlyBracesIntention : RsElementBaseIntentionAction<AddCurlyBracesIntention.Context>() {
    override fun getText() = "Add curly braces"
    override fun getFamilyName() = text

    class Context(
        val useSpeck: RsUseSpeck,
        val path: RsPath,
        val semicolon: PsiElement
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val useItem = element.ancestorStrict<RsUseItem>() ?: return null
        val semicolon = useItem.semicolon ?: return null
        val useSpeck = useItem.useSpeck ?: return null
        val path = useSpeck.path ?: return null
        if (useSpeck.useGroup != null || useSpeck.isStarImport) return null
        return Context(useSpeck, path, semicolon)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val identifier = ctx.path.referenceNameElement

        // Create a new use item that contains a glob list that we can use.
        // Then extract from it the glob list and the double colon.
        val newUseSpeck = RsPsiFactory(project).createUseSpeck("dummy::{${identifier.text}}")
        val newGroup = newUseSpeck.useGroup ?: return
        val newColonColon = newUseSpeck.coloncolon ?: return

        val alias = ctx.useSpeck.alias

        // If there was an alias before, insert it into the new glob item
        if (alias != null) {
            val newGlobItem = newGroup.children[0]
            newGlobItem.addAfter(alias, newGlobItem.lastChild)
        }

        // Remove the identifier from the path by replacing it with its subpath
        val qualifier = ctx.path.path
        if (qualifier != null) {
            ctx.path.replace(qualifier)
        } else {
            ctx.path.delete()
        }

        // Delete the alias of the identifier, if any
        alias?.delete()

        // Insert the double colon and glob list into the use item
        ctx.useSpeck.add(newColonColon)
        ctx.useSpeck.add(newGroup)

        editor.caretModel.moveToOffset(ctx.semicolon.textRange.startOffset - 1)
    }
}
