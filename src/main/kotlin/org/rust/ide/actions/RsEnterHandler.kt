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
import com.intellij.psi.impl.source.tree.injected.InjectedCaret
import org.rust.ide.injected.RsDoctestLanguageInjector
import org.rust.ide.injected.isDoctestInjection
import org.rust.lang.core.psi.RsFile

/**
 * This class is used to handle typing enter inside doctest language injection (see [RsDoctestLanguageInjector]).
 * Enter handlers are piped:
 * [EnterHandler] -> [RsEnterHandler] -> --------------------> [EnterAction.Handler]
 *  |                 |             \ -> [EnterHandler] -> /    ^ just insert new line [originalHandler]
 *  |                 this class          ^ (the case of injected psi) [injectionEnterHandler]
 *  front platform handler (handles indents and other complex stuff)
 */
class RsEnterHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
    private val injectionEnterHandler = EnterHandler(object : EditorActionHandler() {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            originalHandler.execute(editor, caret, dataContext)
        }
    })

    public override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
        return originalHandler.isEnabled(editor, caret, dataContext)
    }

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
        if (editor is EditorWindow && caret is InjectedCaret &&
            (editor.injectedFile as? RsFile)?.isDoctestInjection == true) {
            injectionEnterHandler.execute(editor.delegate, caret.delegate, dataContext)
        } else {
            originalHandler.execute(editor, caret, dataContext)
        }
    }
}
