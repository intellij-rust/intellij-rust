/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections;

/**
 * Tests for the Approximate Constant inspection
 */
class RsApproxConstantInspectionTest : RsInspectionsTestBase(RsApproxConstantInspection()) {

    fun testConstants() = checkByText("""
        fn main() {
            let pi = 3.1;
            let pi = 3.14;
            let pi = <warning descr="Approximate value of `std::f64::consts::PI` found. Consider using it directly.">3.141</warning>;
            let pi = <warning descr="Approximate value of `std::f64::consts::PI` found. Consider using it directly.">3.142</warning>;
            let pi = <warning descr="Approximate value of `std::f32::consts::PI` found. Consider using it directly.">3.141f32</warning>;
            let pi = <warning descr="Approximate value of `std::f64::consts::PI` found. Consider using it directly.">3.1_41f64</warning>;
            let pi = <warning descr="Approximate value of `std::f64::consts::PI` found. Consider using it directly.">3.1416E+0_f64</warning>;
            let log2_e = <warning descr="Approximate value of `std::f64::consts::LOG2_E` found. Consider using it directly.">1.442695040888963</warning>;
        }
    """)

}
