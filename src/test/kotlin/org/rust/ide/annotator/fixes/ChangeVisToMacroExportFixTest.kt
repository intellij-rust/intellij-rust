/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class ChangeVisToMacroExportFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {
    fun `test replace visibility with macro_export`() = checkFixByText("Replace `pub` with `#[macro_export]", """
        <error descr="Visibility modifiers are not applicable to `macro_rules`">pub</error> macro_rules! foo {
            () => ()
        }
    """, """
        #[macro_export]
        macro_rules! foo {
            () => ()
        }
    """)
}
