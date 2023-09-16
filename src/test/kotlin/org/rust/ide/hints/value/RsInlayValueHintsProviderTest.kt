/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.value

import org.rust.ide.hints.type.RsInlayTypeHintsTestBase

class RsInlayValueHintsProviderTest : RsInlayTypeHintsTestBase(RsInlayValueHintsProvider::class) {

    fun `test exclusive range expressions`() = checkByText("""
        fn main() {
            for _ in 1../*hint text="<"*/3 {}
            for _ in 3..=4 {}
            for _ in 5...6 {}
        }
    """)

    fun `test exclusive range patterns`() = checkByText("""
        fn foo(n: i32) {
            match n {
                1../*hint text="<"*/3 => {},
                3..=4 => {},
                5...6 => {},
                _ => {}
            };
        }
    """)
}
