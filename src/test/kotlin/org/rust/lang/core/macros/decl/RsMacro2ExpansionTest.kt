/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.decl

import org.rust.MinRustcVersion
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.macros.RsMacroExpansionTestBase

class RsMacro2ExpansionTest : RsMacroExpansionTestBase() {
    fun `test function-like body`() = doTest("""
        macro foo ($ i:ident) {
            fn $ i() {}
        }
        foo! { bar }
    """, """
        fn bar() {}
    """)

    fun `test macro_rules-like body`() = doTest("""
        macro foo {
            ($ i:ident) => {
                fn $ i() {}
            }
        }
        foo! { bar }
    """, """
        fn bar() {}
    """)

    fun `test stmt context`() = checkSingleMacro("""
       macro foo {
            ($ i:ident, $ j:ident) => {
                struct $ i;
                let $ j = 0;
                ($ i, $ j)
            }
        }

        fn main() {
            foo!(S, a);
        } //^
    """, """
        struct S;
        let a = 0;
        (S, a)
    """)

    fun `test expr context`() = checkSingleMacro("""
       macro foo {
            ($ e:expr) => {
                $ e + 3
            }
        }

        fn main() {
            let _ = foo!(1 + 2);
        }          //^
    """, """
        (1 + 2) + 3
    """)

    @MinRustcVersion("1.52.0")
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test std addr_of`() = checkSingleMacro("""
       fn main() {
           let a = 1;
           let _ = std::ptr::addr_of!(a);
       }                   //^
    """, """
        &raw const a
    """)
}
