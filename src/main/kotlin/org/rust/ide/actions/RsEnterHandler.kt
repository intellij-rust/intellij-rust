/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.codeInsight.editorActions.EnterHandler
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actions.EnterAction
import org.rust.ide.injected.RsDoctestLanguageInjector
import org.rust.ide.injected.isDoctestInjection
import org.rust.lang.core.psi.RsFile

/**
 * This class is used to handle enter typing inside doctest language injection (see [RsDoctestLanguageInjector]).
 *
 * Enter handlers are piped:
 * [EnterHandler] -> [RsEnterHandler] -> [EnterAction.Handler]
 *  |                 ^ this class        ^ just insert new line [originalHandler]
 *  front platform handler (handles indents and other complex stuff)
 */
class RsEnterHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
    public override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
        // If we forbid enter handling inside language injections in Rustdoc code blocks, then
        // enter is handled outside of the injection (i.e., inside the doc comment)
        val isDoctestInjection = editor is EditorWindow &&
            (editor.injectedFile as? RsFile)?.isDoctestInjection == true
        return !isDoctestInjection && originalHandler.isEnabled(editor, caret, dataContext)
    }

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
        originalHandler.execute(editor, caret, dataContext)
    }
}
