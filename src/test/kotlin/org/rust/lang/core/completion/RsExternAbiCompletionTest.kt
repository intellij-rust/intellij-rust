/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.rust.MockRustcVersion

@MockRustcVersion("1.54.0")
class RsExternAbiCompletionTest : RsCompletionTestBase() {

    fun `test stable abi`() = doSingleCompletion("""
        extern "cde/*caret*/" fn extern_fn() {}
    """, """
        extern "cdecl/*caret*/" fn extern_fn() {}
    """)

    fun `test do not complete experimental abi on stable`() = checkNoCompletion("""
        extern "x86/*caret*/" fn extern_fn() {}
    """)

    fun `test do not complete abi in string literals`() = checkNoCompletion("""
        fn main() {
            let a = "fastca/*caret*/";
        }
    """)

    @MockRustcVersion("1.56.0-nightly")
    fun `test experimental abi`() = doSingleCompletion("""
        extern "C-unw/*caret*/" fn extern_fn() {}
    """, """
        #![feature(c_unwind)]

        extern "C-unwind/*caret*/" fn extern_fn() {}
    """)

    @MockRustcVersion("1.56.0-nightly")
    fun `test do not add existing feature attribute`() = doSingleCompletion("""
        #![feature(abi_x86_interrupt)]

        extern "x86/*caret*/" fn extern_fn() {}
    """, """
        #![feature(abi_x86_interrupt)]

        extern "x86-interrupt/*caret*/" fn extern_fn() {}
    """)
}
