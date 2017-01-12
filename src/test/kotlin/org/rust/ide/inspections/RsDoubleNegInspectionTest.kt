package org.rust.ide.inspections

/**
 * Tests for Double Negation inspection.
 */
class RsDoubleNegInspectionTest : RsInspectionsTestBase() {

    fun testSimple() = checkByText<RsDoubleNegInspection>("""
        fn main() {
            let a = 12;
            let b = <warning descr="--x could be misinterpreted as a pre-decrement, but effectively is a no-op">--a</warning>;
        }
    """)

    fun testWithSpaces() = checkByText<RsDoubleNegInspection>("""
        fn main() {
            let i = 10;
            while <warning descr="--x could be misinterpreted as a pre-decrement, but effectively is a no-op">- - i</warning> > 0 {}
        }
    """)

    fun testExpression() = checkByText<RsDoubleNegInspection>("""
        fn main() {
            let a = 7;
            println!("{}",  <warning descr="--x could be misinterpreted as a pre-decrement, but effectively is a no-op">--(2*a + 1)</warning>);
        }
    """)
}
