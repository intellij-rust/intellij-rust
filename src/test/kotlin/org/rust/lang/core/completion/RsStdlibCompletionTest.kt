/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.testFramework.LightProjectDescriptor

class RsStdlibCompletionTest : RsCompletionTestBase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibRustProjectDescriptor

    fun `test prelude`() = @Suppress("DEPRECATION") checkSingleCompletion("drop()", """
        fn main() {
            dr/*caret*/
        }
    """)

    fun `test prelude visibility`() = checkNoCompletion("""
        mod m {}
        fn main() {
            m::dr/*caret*/
        }
    """)

    fun `test iter`() = @Suppress("DEPRECATION") checkSingleCompletion("iter_mut()", """
        fn main() {
            let vec: Vec<i32> = Vec::new();
            let iter = vec.iter_m/*caret*/
        }
    """)

    fun `test derived trait method`() = @Suppress("DEPRECATION") checkSingleCompletion("fmt", """
        #[derive(Debug)]
        struct Foo;
        fn bar(foo: Foo) {
            foo.fm/*caret*/
        }
    """)

    fun `test macro`() = doSingleCompletion("""
        fn main() { unimpl/*caret*/ }
    """, """
        fn main() { unimplemented!(/*caret*/) }
    """)

    fun `test macro with square brackets`() = doSingleCompletion("""
        fn main() { vec/*caret*/ }
    """, """
        fn main() { vec![/*caret*/] }
    """)
}

