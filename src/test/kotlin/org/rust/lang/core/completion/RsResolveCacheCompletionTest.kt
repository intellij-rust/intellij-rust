/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.psi.PsiDocumentManager
import org.intellij.lang.annotations.Language

class RsResolveCacheCompletionTest : RsCompletionTestBase() {
    fun test() = doTest("""
        pub mod l1 {
            pub mod l2 {
                pub fn foo() {}
            }
        }
        fn main() {
            l1/*caret*/
        }
    """, """
        pub mod l1 {
            pub mod l2 {
                pub fn foo() {}
            }
        }
        fn main() {
            l1::l2::foo()/*caret*/
        }
    """, "::", "::")

    private fun doTest(@Language("Rust") before: String, @Language("Rust") after: String, vararg toType: String) {
        InlineFile(before).withCaret()

        toType.forEach {
            myFixture.type(it)
            PsiDocumentManager.getInstance(project).commitAllDocuments() // process PSI modification events
            executeSoloCompletion()
        }

        myFixture.checkResult(replaceCaretMarker(after))
    }
}
