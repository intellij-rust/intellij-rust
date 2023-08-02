/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.MockRustcVersion

class RsFunctionSignatureSyntaxTest : RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test E0045 error when use variadic parameter on non-C ABI`() = checkErrors("""
        extern "Rust" {
            /*error descr="C-variadic function must have a compatible calling convention, like `C` or `cdecl` [E0045]"*/fn foo(x: u8, ...);/*error**/
        }
    """)

    fun `test E0045 no error when use variadic parameter on C ABI`() = checkErrors("""
        extern "C" {
            fn foo (x: u8, ...);
        }
    """)

    fun `test E0045 no error when use variadic parameter on cdecl ABI`() = checkErrors("""
        extern "cdecl" {
            fn foo (x: u8, ...);
        }
    """)

    fun `test E0045 no error when use variadic parameter on default ABI`() = checkErrors("""
        extern {
            fn foo (x: u8, ...);
        }
    """)

    @MockRustcVersion("1.60.0-nightly")
    fun `test E0045 no error when use variadic parameter in extern function declaration`() = checkErrors("""
        #![feature(c_variadic)]
        unsafe extern fn foo(x: u8, ...) -> bool {
            x == 1
        }
    """)
}
