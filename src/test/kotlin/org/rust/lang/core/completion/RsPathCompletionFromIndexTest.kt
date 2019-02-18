/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.intellij.lang.annotations.Language
import org.rust.hasCaretMarker
import org.rust.lang.core.completion.RsCommonCompletionProvider.Testmarks
import org.rust.openapiext.Testmark

class RsPathCompletionFromIndexTest : RsCompletionTestBase() {

    fun `test suggest an non-imported symbol from index and add proper import`() = doSingleCompletion("""
        mod collections {
            pub struct BTreeMap;
        }

        fn main() {
            let _ = BTreeM/*caret*/
        }
    """, """
        use collections::BTreeMap;

        mod collections {
            pub struct BTreeMap;
        }

        fn main() {
            let _ = BTreeMap/*caret*/
        }
    """)

    fun `test doesn't suggest a symbol that already in scope`() = doSingleCompletion("""
        use collections::BTreeMap;

        mod collections {
            pub struct BTreeMap;
        }

        fn main() {
            let _ = BTreeM/*caret*/
        }
    """, """
        use collections::BTreeMap;

        mod collections {
            pub struct BTreeMap;
        }

        fn main() {
            let _ = BTreeMap/*caret*/
        }
    """)

    fun `test doesn't suggest a symbol that leads to name collision`() = doSingleCompletion("""
        struct BTreeMap;

        mod collections {
            pub struct BTreeMap;
        }

        fn main() {
            let _ = BTreeM/*caret*/
        }
    """, """
        struct BTreeMap;

        mod collections {
            pub struct BTreeMap;
        }

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

    fun `test enum completion`() = doSingleCompletion("""
        mod a {
            pub enum Enum {
                V1, V2
            }
        }

        fn main() {
            let a = Enu/*caret*/
        }
    """, """
        use a::Enum;

        mod a {
            pub enum Enum {
                V1, V2
            }
        }

        fn main() {
            let a = Enum/*caret*/
        }
    """)

    fun `test insert handler`() = doSingleCompletion("""
        mod foo {
            pub fn bar(x: i32) {}
        }

        fn main() {
            ba/*caret*/
        }
    """, """
        use foo::bar;

        mod foo {
            pub fn bar(x: i32) {}
        }

        fn main() {
            bar(/*caret*/)
        }
    """)

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
