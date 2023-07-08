/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.intentions.util.macros.InvokeInside
import org.rust.ide.utils.PsiModificationUtil
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsUseGroup
import org.rust.lang.core.psi.RsUseSpeck
import org.rust.lang.core.psi.ext.*

/**
 * Removes the curly braces on singleton imports, changing from this
 *
 * ```
 * use std::{mem};
 * ```
 *
 * to this:
 *
 * ```
 * use std::mem;
 * ```
 */
class RemoveCurlyBracesIntention : RsElementBaseIntentionAction<RemoveCurlyBracesIntention.Context>() {
    override fun getText() = RsBundle.message("intention.name.remove.curly.braces")
    override fun getFamilyName() = text

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    data class Context(
        val path: RsPath,
        val useGroup: RsUseGroup,
        val name: String
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        for (ancestor in element.ancestors) {
            val speck = ancestor as? RsUseSpeck ?: continue
            return createContextIfCompatible(speck) ?: continue
        }

        return null
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        // Save the cursor position, adjusting for curly brace removal
        val caret = editor.caretModel.offset
        val newOffset = when {
            caret < ctx.useGroup.startOffset -> caret
            caret < ctx.useGroup.endOffset -> caret - 1
            else -> caret - 2
        }

        removeCurlyBracesFromUseSpeck(ctx)

        editor.caretModel.moveToOffset(newOffset)
    }

    companion object {
        fun createContextIfCompatible(useSpeck: RsUseSpeck): Context? {
            val useGroup = useSpeck.useGroup ?: return null
            val path = useSpeck.path ?: return null
            val name = useGroup.asTrivial?.text ?: return null
            if (!PsiModificationUtil.canReplace(useGroup)) return null
            return Context(path, useGroup, name)
        }

        fun removeCurlyBracesFromUseSpeck(ctx: Context) {
            val (path, useGroup, name) = ctx

            // Conjure up a new use item to make a new path containing the
            // identifier we want; then grab the relevant parts
            val newUseSpeck = RsPsiFactory(useGroup.project).createUseSpeck("dummy::$name")
            val newPath = newUseSpeck.path ?: return
            val newSubPath = newPath.basePath()

            // Attach the identifier to the old path, then splice that path into
            // the use item. Delete the old glob list and attach the alias, if any.
            newSubPath.replace(path.copy())
            path.replace(newPath)
            useGroup.parentUseSpeck.coloncolon?.delete()
            val alias = newUseSpeck.alias
            if (alias != null) {
                useGroup.replace(alias.copy())
            } else {
                useGroup.delete()
            }
        }
    }
}
