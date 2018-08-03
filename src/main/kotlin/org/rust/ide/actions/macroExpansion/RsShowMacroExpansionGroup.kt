/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.macroExpansion

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import org.rust.lang.core.psi.RsMacroCall

/**
 * Action group for showing macros expansion actions in context menu.
 *
 * It is required to show those actions only on certain elements (like [RsMacroCall]s).
 */
class RsShowMacroExpansionGroup : DefaultActionGroup() {
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = getMacroUnderCaret(event.dataContext) != null
    }
}
