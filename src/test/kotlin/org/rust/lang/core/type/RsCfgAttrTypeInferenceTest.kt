/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.rust.MockAdditionalCfgOptions

class RsCfgAttrTypeInferenceTest : RsTypificationTestBase() {
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test function parameter under cfg 1`() = testExpr("""
        fn foo(
            #[cfg(intellij_rust)]      a: u8,
            #[cfg(not(intellij_rust))] a: i8,
        ) {}
        fn main() {
            let a = 0;
            foo(a);
            a;
        } //^ u8
    """)

    fun `test function parameter under cfg 2`() = testExpr("""
        fn foo(
            #[cfg(intellij_rust)]      a: u8,
            #[cfg(not(intellij_rust))] a: i8,
        ) {}
        fn main() {
            let a = 0;
            foo(a);
            a;
        } //^ i8
    """)
}
