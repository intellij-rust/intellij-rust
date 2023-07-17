/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import org.intellij.lang.annotations.Language
import org.rust.FileTreeBuilder
import org.rust.fileTree
import org.rust.lang.core.completion.RsCompletionTestBase

class RsCfgFeatureCompletionProviderTest : RsCompletionTestBase() {
    fun `test simple in literal`() = doSingleCompletionByFileTree({
        toml("Cargo.toml", """
            [features]
            foo = []
        """)
        rust("main.rs", """
            #[cfg(feature = "/*caret*/")]
            fn foo() {}
        """)
    }, """
        #[cfg(feature = "foo")]
        fn foo() {}
    """)

    fun `test simple without literal`() = doSingleCompletionByFileTree({
        toml("Cargo.toml", """
            [features]
            foo = []
        """)
        rust("main.rs", """
            #[cfg(feature = /*caret*/)]
            fn foo() {}
        """)
    }, """
        #[cfg(feature = "foo")]
        fn foo() {}
    """)

    fun `test complex in literal`() = doSingleCompletionByFileTree({
        toml("Cargo.toml", """
            [features]
            foo = []
            bar = []
            qux = []
        """)
        rust("main.rs", """
            #[cfg(any(feature = "foo", feature = "b/*caret*/", feature = "qux"))]
            fn foo() {}
        """)
    }, """
        #[cfg(any(feature = "foo", feature = "bar", feature = "qux"))]
        fn foo() {}
    """)

    fun `test complex without literal`() = doSingleCompletionByFileTree({
        toml("Cargo.toml", """
            [features]
            foo = []
            bar = []
            qux = []
        """)
        rust("main.rs", """
            #[cfg(any(feature = "foo", feature = b/*caret*/, feature = "qux"))]
            fn foo() {}
        """)
    }, """
        #[cfg(any(feature = "foo", feature = "bar", feature = "qux"))]
        fn foo() {}
    """)

    private fun doSingleCompletionByFileTree(builder: FileTreeBuilder.() -> Unit, @Language("Rust") after: String) {
        completionFixture.doSingleCompletionByFileTree(fileTree(builder), after, forbidAstLoading = false)
    }
}
