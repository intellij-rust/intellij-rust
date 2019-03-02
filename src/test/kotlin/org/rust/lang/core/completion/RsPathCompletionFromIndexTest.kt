/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.intellij.lang.annotations.Language
import org.rust.hasCaretMarker
import org.rust.ide.settings.RsCodeInsightSettings
import org.rust.lang.core.completion.RsCommonCompletionProvider.Testmarks
import org.rust.openapiext.Testmark

class RsPathCompletionFromIndexTest : RsCompletionTestBase() {

    fun `test suggest an non-imported symbol from index and add proper import`() = doTest("""
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

    fun `test doesn't suggest a symbol that already in scope`() = doTest("""
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

    fun `test doesn't suggest a symbol that leads to name collision`() = doTest("""
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

    fun `test doesn't suggest an non-imported symbol from index when setting disabled`() = doTest("""
        struct BTreeMap;

        mod collections {
            pub struct BTreeMap;
            pub struct BTreeSet;
        }

        fn main() {
            let _ = BTree/*caret*/
        }
    """, """
        struct BTreeMap;

        mod collections {
            pub struct BTreeMap;
            pub struct BTreeSet;
        }

        fn main() {
            let _ = BTreeMap/*caret*/
        }
    """, suggestOutOfScopeItems = false)

    fun `test doesn't suggest symbols from index for empty path`() = doTest("""
        pub mod foo {}
        fn main() {
            let _ = /*caret*/;
        }
    """, Testmarks.pathCompletionFromIndex)

    fun `test enum completion`() = doTest("""
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

    fun `test insert handler`() = doTest("""
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

    fun `test do not import out of scope items when setting disabled`() = doTest("""
        mod collections {
            pub struct BTreeMap;
        }

        fn main() {
            let _ = BTreeM/*caret*/
        }
    """, """
        mod collections {
            pub struct BTreeMap;
        }

        fn main() {
            let _ = BTreeMap/*caret*/
        }
    """, importOutOfScopeItems = false)

    private fun doTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        suggestOutOfScopeItems: Boolean = true,
        importOutOfScopeItems: Boolean = true
    ) {
        val settings = RsCodeInsightSettings.getInstance()
        val suggestInitialValue = settings.suggestOutOfScopeItems
        val importInitialValue = settings.importOutOfScopeItems
        settings.suggestOutOfScopeItems = suggestOutOfScopeItems
        settings.importOutOfScopeItems = importOutOfScopeItems
        try {
            doSingleCompletion(before, after)
        } finally {
            settings.suggestOutOfScopeItems = suggestInitialValue
            settings.importOutOfScopeItems = importInitialValue
        }
    }

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
