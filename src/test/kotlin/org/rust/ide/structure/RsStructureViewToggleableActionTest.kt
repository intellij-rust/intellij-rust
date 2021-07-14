/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.structure

import org.intellij.lang.annotations.Language

abstract class RsStructureViewToggleableActionTest : RsStructureViewTestBase() {
    protected abstract val actionId: String

    protected fun doTest(@Language("Rust") code: String, disabled: String, enabled: String) =
        doTestStructureView(code) {
            setActionActive(actionId, false)
            assertTreeEqual(tree, disabled.trimMargin())
            setActionActive(actionId, true)
            assertTreeEqual(tree, enabled.trimMargin())
        }
}
