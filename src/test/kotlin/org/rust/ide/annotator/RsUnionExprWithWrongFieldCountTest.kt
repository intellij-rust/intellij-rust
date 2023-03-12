/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsUnionExprWithWrongFieldCountTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {
    fun `test E0784 union literal ok`() = checkErrors("""
        union U { a: u8, b: u16 }
        fn main() {
            _ = U { a: 0 };
            _ = U { b: 0 };
        }
    """)

    fun `test E0784 union literal with wrong fields count`() = checkErrors("""
        union U { a: u8, b: u16 }
        fn main() {
            _ = /*error descr="Union expressions should have exactly one field [E0784]"*/U {}/*error**/;
            _ = /*error descr="Union expressions should have exactly one field [E0784]"*/U { a: 0, b: 1 }/*error**/;
            _ = /*error descr="Union expressions should have exactly one field [E0784]"*/U { a: 0, b: 1, c: 2 }/*error**/;
        }
    """)

    fun `test E0784 union literal with struct update instead of fields`() = checkErrors("""
        union U { a: u8, b: u16 }
        fn main() {
             _ = /*error descr="Union expressions should have exactly one field [E0784]"*/U { ..u }/*error**/;
        }
    """)
}
