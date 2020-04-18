/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith

import com.intellij.codeInsight.CodeInsightSettings
import org.rust.RsTestBase

class RsSurroundWithTest : RsTestBase() {

    // https://github.com/intellij-rust/intellij-rust/issues/5256
    fun `test insert pair curly brace in use item`() =
        withOptionValue(CodeInsightSettings.getInstance()::SURROUND_SELECTION_ON_QUOTE_TYPED, true) {
            checkByText(
                "use std::<selection>mem</selection>;",
                "use std::{mem};"
            ) {
                myFixture.type("{")
            }
        }
}
