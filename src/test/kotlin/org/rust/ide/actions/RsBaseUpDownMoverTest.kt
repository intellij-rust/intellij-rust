/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.openapi.actionSystem.IdeActions
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase

abstract class RsBaseUpDownMoverTest : RsTestBase() {
    fun moveBothDirectionTest(@Language("Rust") down: String, @Language("Rust") up: String) {
        checkByText(up, down) {
            myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION)
        }
        checkByText(down, up) {
            myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)
        }
    }
}
