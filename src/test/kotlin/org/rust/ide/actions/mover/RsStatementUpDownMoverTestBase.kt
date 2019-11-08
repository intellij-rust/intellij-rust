/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapiext.Testmark
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

abstract class RsStatementUpDownMoverTestBase : RsTestBase() {
    fun moveDown(
        @Language("Rust") before: String,
        @Language("Rust") after: String = before,
        testmark: Testmark? = null
    ) = doTest(before, after, IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION, testmark)

    fun moveUp(
        @Language("Rust") before: String,
        @Language("Rust") after: String = before,
        testmark: Testmark? = null
    ) = doTest(before, after, IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION, testmark)

    fun moveDownAndBackUp(
        @Language("Rust") down: String,
        @Language("Rust") up: String = down,
        testmark: Testmark? = null
    ) {
        moveDown(down, up, testmark)
        moveUp(up, down, testmark)
    }

    private fun doTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String = before,
        actionId: String,
        testmark: Testmark? = null
    ) {
        val action = {
            checkByText(before.trimIndent() + "\n", after.trimIndent() + "\n") {
                myFixture.performEditorAction(actionId)
            }
        }
        testmark?.checkHit(action) ?: action()
    }
}
