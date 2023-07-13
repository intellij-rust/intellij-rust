/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

/**
 * Tests for Double Negation inspection.
 */
class RsDoubleNegInspectionTest : RsInspectionsTestBase(RsDoubleNegInspection::class) {

    fun `test simple`() = checkByText("""
        fn main() {
            let a = 12;
            let b = <warning descr="--x could be misinterpreted as a pre-decrement, but effectively is a no-op">--a</warning>;
        }
    """)

    fun `test with spaces`() = checkByText("""
        fn main() {
            let i = 10;
            while <warning descr="--x could be misinterpreted as a pre-decrement, but effectively is a no-op">- - i</warning> > 0 {}
        }
    """)

    fun `test expression`() = checkByText("""
        fn main() {
            let a = 7;
            println!("{}",  <warning descr="--x could be misinterpreted as a pre-decrement, but effectively is a no-op">--(2*a + 1)</warning>);
        }
    """)

    fun `test prefix decrement operator top expr`() = checkFixByText("Replace with `-= 1`", """
        fn main() {
            let mut a = 0;
            <warning descr="--x could be misinterpreted as a pre-decrement, but effectively is a no-op">--/*caret*/a</warning>;
        }
    """, """
        fn main() {
            let mut a = 0;
            a -= 1;
        }
    """)

    fun `test prefix decrement operator nested expr`() = checkFixIsUnavailable("Replace with `+= 1`", """
        fn main() {
            let mut a = 0;
            <warning descr="--x could be misinterpreted as a pre-decrement, but effectively is a no-op">--/*caret*/a</warning> < 1;
        }
    """)
}
