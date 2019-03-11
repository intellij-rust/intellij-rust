/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.intellij.lang.annotations.Language
import org.rust.lang.core.types.ty.*

class RsPrimitiveTypeCompletionTest : RsCompletionTestBase() {

    fun `test return type`() = doTest("""
        fn foo() -> /*caret*/
    """)

    fun `test type parameter`() = doTest("""
        fn main() {
            let v: Vec</*caret*/> = Vec::new();
        }
    """)

    private fun doTest(@Language("Rust") text: String) {
        val primitiveTypes = TyInteger.VALUES + TyFloat.VALUES + TyBool + TyStr + TyChar
        for (ty in primitiveTypes) {
            checkContainsCompletion(ty.name, text)
        }
    }
}
