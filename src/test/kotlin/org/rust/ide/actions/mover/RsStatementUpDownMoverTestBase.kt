/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

import com.intellij.openapi.actionSystem.IdeActions
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

abstract class RsStatementUpDownMoverTestBase : RsTestBase() {
    fun moveDown(
        @Language("Rust") before: String,
        @Language("Rust") after: String = before,
    ) = doTest(before, after, IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)

    fun moveUp(
        @Language("Rust") before: String,
        @Language("Rust") after: String = before,
    ) = doTest(before, after, IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION)

    fun moveDownAndBackUp(
        @Language("Rust") down: String,
        @Language("Rust") up: String = down,
    ) {
        moveDown(down, up)
        moveUp(up, down)
    }

    private fun doTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String = before,
        actionId: String,
    ) {
        checkEditorAction(before.trimIndent() + "\n", after.trimIndent() + "\n", actionId, false)
    }
}
