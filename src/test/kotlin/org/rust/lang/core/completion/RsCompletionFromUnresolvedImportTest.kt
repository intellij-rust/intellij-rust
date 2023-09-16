/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.rust.MockAdditionalCfgOptions
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class RsCompletionFromUnresolvedImportTest : RsCompletionTestBase(){

    fun `test simple`() = checkContainsCompletion("foo", """
        use inner::foo;
        fn main() {
            f/*caret*/
        }
    """)

    fun `test local import`() = checkContainsCompletion("foo", """
        fn main() {
            use inner::foo;
            f/*caret*/
        }
    """)

    fun `test alias`() = checkContainsCompletion("foo", """
        use inner::bar as foo;
        fn main() {
            f/*caret*/
        }
    """)

    fun `test unnamed alias`() = checkNotContainsCompletion(listOf("foo", "_"), """
        use inner::foo as _;
        fn main() {
            /*caret*/
        }
    """)

    fun `test glob import`() = checkNotContainsCompletion("foo", """
        use inner::foo::*;
        fn main() {
            f/*caret*/
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg-disabled import`() = checkNotContainsCompletion("foo", """
        #[cfg(not(intellij_rust))]
        use inner::foo;
        fn main() {
            f/*caret*/
        }
    """)

    fun `test resolved import`() = doSingleCompletion("""
        mod inner { pub fn foo() {} }
        use inner::foo;
        fn main() {
            fo/*caret*/
        }
    """, """
        mod inner { pub fn foo() {} }
        use inner::foo;
        fn main() {
            foo()/*caret*/
        }
    """)

    fun `test multi resolved path`() = checkNotContainsCompletion("foo", """
        use inner::foo;
        mod inner {
            pub fn foo() {}
            pub fn foo() {}
        }
        fn main() {
            let _: fo/*caret*/
        }
    """)

    fun `test qualified path`() = checkNotContainsCompletion("foo", """
        use inner::foo;
        fn main() {
            bar::fo/*caret*/
        }
    """)

    fun `test absolute path`() = checkNotContainsCompletion("foo", """
        use inner::foo;
        fn main() {
            ::fo/*caret*/
        }
    """)

    fun `test type qualified path`() = checkNotContainsCompletion("foo", """
        use inner::foo;
        fn main() {
            <Foo>::fo/*caret*/
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test should have low priority`() = doFirstCompletion("""
        use std::dropunresolved;
        fn main() {
            dr/*caret*/
        }
    """, """
        use std::dropunresolved;
        fn main() {
            drop(/*caret*/)
        }
    """)
}
