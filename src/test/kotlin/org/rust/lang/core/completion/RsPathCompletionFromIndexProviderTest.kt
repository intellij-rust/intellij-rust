/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.hasCaretMarker
import org.rust.lang.core.completion.RsCommonCompletionProvider.Testmarks
import org.rust.openapiext.Testmark

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsPathCompletionFromIndexProviderTest : RsCompletionTestBase() {
    fun `test suggest an non-imported symbol from index and add proper import`() = doSingleCompletion("""
        fn main() {
            let _ = BTreeM/*caret*/
        }
    """, """
        use std::collections::BTreeMap;

        fn main() {
            let _ = BTreeMap/*caret*/
        }
    """)

    fun `test doesn't suggest a symbol that already in scope`() = doSingleCompletion("""
        use std::collections::BTreeMap;

        fn main() {
            let _ = BTreeM/*caret*/
        }
    """, """
        use std::collections::BTreeMap;

        fn main() {
            let _ = BTreeMap/*caret*/
        }
    """)

    fun `test doesn't suggest a symbol that leads to name collision`() = doSingleCompletion("""
        struct BTreeMap;

        fn main() {
            let _ = BTreeM/*caret*/
        }
    """, """
        struct BTreeMap;

        fn main() {
            let _ = BTreeMap/*caret*/
        }
    """)

    fun `test doesn't suggest symbols from index for empty path`() = doTest("""
        pub mod foo {}
        fn main() {
            let _ = /*caret*/;
        }
    """, Testmarks.pathCompletionFromIndex)

    private fun doTest(@Language("Rust") text: String, testmark: Testmark) {
        check(hasCaretMarker(text)) {
            "Please add `/*caret*/` marker"
        }
        myFixture.configureByText("main.rs", replaceCaretMarker(text))
        testmark.checkNotHit {
            myFixture.completeBasicAllCarets(null)
        }
    }
}
