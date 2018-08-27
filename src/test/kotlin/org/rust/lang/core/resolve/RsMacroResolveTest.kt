/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

class RsMacroResolveTest : RsResolveTestBase() {
    private val `$` = '$'
    fun `test resolve simple matching with multiple pattern definition`() = checkByCode("""
        macro_rules! test {
            ($`$`test:expr) => (
               //X
                $`$`test
                 //^
            )
            ($`$`test:expr) => (
                $`$`test
            )
        }
    """)

    fun `test resolve simple matching with multiple matching`() = checkByCode("""
        macro_rules! test {
            ($`$`test:expr, $`$`ty:ty) => (
               //X
                $`$`test
                 //^
            )
        }
    """)

    fun `test resolve simple matching in complex matching`() = checkByCode("""
        macro_rules! test {
            ($`$`($`$`test:expr),+, $`$`ty:ty) => (
              //X
                $`$`($`$`test),+
                  //^
            )
        }
    """)

    fun `test resolve macro same scope`() = checkByCode("""
        macro_rules! foo_bar { () => () }
        //X
        foo_bar!();
        //^
    """)

    fun `test resolve macro in function`() = checkByCode("""
        macro_rules! foo_bar { () => () }
        //X
        fn main() {
            foo_bar!();
            //^
        }
    """)

    fun `test resolve macro mod lower`() = checkByCode("""
        macro_rules! foo_bar { () => () }
        //X
        mod b {
            fn main() {
                foo_bar!();
                //^
            }
        }
    """)

    fun `test resolve macro mod`() = checkByCode("""
        #[macro_use]
        mod a {
            macro_rules! foo_bar { () => () }
            //X
        }
        mod b {
            fn main() {
                foo_bar!();
                //^
            }
        }
    """)

    // Macros are scoped by lexical order
    // see https://github.com/intellij-rust/intellij-rust/issues/1474
    fun `test resolve macro mod wrong order`() = expect<IllegalStateException> {
        checkByCode("""
        mod b {
            fn main() {
                foo_bar!();
                //^ unresolved
            }
        }
        #[macro_use]
        mod a {
            macro_rules! foo_bar { () => () }
        }
    """)
    }

    fun `test resolve macro missing macro_use`() = checkByCode("""
        // Missing #[macro_use] here
        mod a {
            macro_rules! foo_bar { () => () }
        }
        fn main() {
            foo_bar!();
            //^ unresolved
        }
    """, NameResolutionTestmarks.missingMacroUse)

    fun `test resolve macro missing macro_use mod`() = checkByCode("""
        // Missing #[macro_use] here
        mod a {
            macro_rules! foo_bar { () => () }
        }
        mod b {
            fn main() {
                foo_bar!();
                //^ unresolved
            }
        }
    """, NameResolutionTestmarks.missingMacroUse)

    // More macro tests in [RsPackageLibraryResolveTest] and [RsStubOnlyResolveTest]
}
