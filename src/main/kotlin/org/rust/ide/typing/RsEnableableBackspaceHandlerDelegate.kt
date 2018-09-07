/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

abstract class RsEnableableBackspaceHandlerDelegate : BackspaceHandlerDelegate() {
    private var enabled: Boolean = false

    override fun beforeCharDeleted(char: Char, file: PsiFile, editor: Editor) {
        enabled = deleting(char, file, editor)
    }

    override fun charDeleted(char: Char, file: PsiFile, editor: Editor): Boolean {
        if (!enabled) return false
        return deleted(char, file, editor)
    }

    /** Determine whether this handler applies to given context and perform necessary actions before deleting [char]. */
    open fun deleting(char: Char, file: PsiFile, editor: Editor): Boolean = true

    /**
     * Perform action after [char] was deleted.
     *
     * @return true whether this handler succeeded and the IDE should stop evaluating remaining handlers;
     *         otherwise, false
     */
    open fun deleted(char: Char, file: PsiFile, editor: Editor): Boolean = false
}
