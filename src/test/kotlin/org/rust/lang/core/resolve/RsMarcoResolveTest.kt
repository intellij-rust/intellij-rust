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
}
