/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.testFramework.LightProjectDescriptor

class RsStdlibCompletionTest : RsCompletionTestBase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibRustProjectDescriptor

    fun testPrelude() = checkSingleCompletion("drop()", """
        fn main() {
            dr/*caret*/
        }
    """)

    fun testPreludeVisibility() = checkNoCompletion("""
        mod m {}
        fn main() {
            m::dr/*caret*/
        }
    """)

    fun testIter() = checkSingleCompletion("iter_mut()", """
        fn main() {
            let vec: Vec<i32> = Vec::new();
            let iter = vec.iter_m/*caret*/
        }
    """)

    fun testDerivedTraitMethod() = checkSingleCompletion("fmt", """
        #[derive(Debug)]
        struct Foo;
        fn bar(foo: Foo) {
            foo.fm/*caret*/
        }
    """)

    fun testMarcoDefinitionPrintln() = checkSingleCompletion("eprintln", """
        fn main() {
            eprintl/*caret*/
        }
    """)
}

