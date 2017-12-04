/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.formatter.processors.asTrivial
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict

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
// TODO: this really should reuse code from RsSingleImportRemoveBracesFormatProcessor.
class RemoveCurlyBracesIntention : RsElementBaseIntentionAction<RemoveCurlyBracesIntention.Context>() {
    override fun getText() = "Remove curly braces"
    override fun getFamilyName() = text

    data class Context(
        val useSpeck: RsUseSpeck,
        val path: RsPath,
        val useGroup: RsUseGroup,
        val name: String
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RemoveCurlyBracesIntention.Context? {
        val useItem = element.ancestorStrict<RsUseItem>() ?: return null
        val useSpeck = useItem.useSpeck ?: return null
        val path = useSpeck.path ?: return null
        val useGroup = useSpeck.useGroup ?: return null
        val (_, _, name) = useGroup.asTrivial ?: return null

        return Context(
            useSpeck,
            path,
            useGroup,
            name
        )
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (useSpeck, path, useGroup, name) = ctx

        // Save the cursor position, adjusting for curly brace removal
        val caret = editor.caretModel.offset
        val newOffset = when {
            caret < useGroup.textRange.startOffset -> caret
            caret < useGroup.textRange.endOffset -> caret - 1
            else -> caret - 2
        }

        // Conjure up a new use item to make a new path containing the
        // identifier we want; then grab the relevant parts
        val newUseSpeck = RsPsiFactory(project).createUseSpeck("dummy::$name")
        val newPath = newUseSpeck.path ?: return
        val newSubPath = newPath.path ?: return

        // Attach the identifier to the old path, then splice that path into
        // the use item. Delete the old glob list and attach the alias, if any.
        newSubPath.replace(path.copy())
        path.replace(newPath)
        useSpeck.coloncolon?.delete()
        useGroup.delete()

        editor.caretModel.moveToOffset(newOffset)
    }
}
