/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.intellij.lang.annotations.Language
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.types.type


class RsFunctorTypeInferenceTest: RsTypificationTestBase() {
    fun `test if with all branches diverging`() = testFunctor("""
        fn main() {
            let x = if true { return; } else { return; };
            x;
        }
      //^ ()
    """)

    fun `test one branch diverges`() = testFunctor("""
        fn main() {
            let x = if true { return; } else { 92 };
            x;
        }
      //^ ()
    """)

    fun `test block diverges with explicit type annotation`() = testFunctor("""
        fn main() {
            let x = { let _: i32 = loop {}; 92 };
            x;
        }
      //^ ()
    """)

    fun `test match with divergence`() = testFunctor("""
        enum Buck { Full(u32), Empty }
        fn peek() -> Buck { Buck::Empty }

        fn stuff() -> u32 {
            let full = match peek() {
                Buck::Empty => {
                    return 0;
                }
                Buck::Full(bucket) => bucket,
            };
            full
        }
      //^ u32
    """)

    fun `test diverge with if closure`() = testFunctor("""
        fn main() {
            let f = |x: i32| {
                if x > 10 {
                    return 1;
                }
                unimplemented!();
            };
          //^ i32
        }
    """)

    private fun testFunctor(@Language("Rust") code: String) {
        InlineFile(code)
        val (block, expectedType) = findElementAndDataInEditor<RsBlock>()
        check(block.type.toString() == expectedType) {
            "Type mismatch. Expected: $expectedType, found: ${block.type}."
        }
    }
}
