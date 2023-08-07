/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

/**
 * Tests for the Approximate Constant inspection
 */
class RsApproxConstantInspectionTest : RsInspectionsTestBase(RsApproxConstantInspection::class) {

    fun `test constants`() = checkByText("""
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

    fun `test constants with type inference`() = checkByText("""
        const PI: f32 = <warning descr="Approximate value of `std::f32::consts::PI` found. Consider using it directly.">3.1415</warning>;
        const PI: f64 = <warning descr="Approximate value of `std::f64::consts::PI` found. Consider using it directly.">3.1415</warning>;
    """)

    fun `test use core when no std lib`() = checkByText("""
        #![no_std]
        const PI: f64 = <warning descr="Approximate value of `core::f64::consts::PI` found. Consider using it directly.">3.1415</warning>;
    """)

    fun `test unavailable when no core lib`() = checkByText("""
        #![no_core]
        const PI: f64 = 3.1415;
    """)

    fun `test replace with predefined fix`() = checkFixByText("Replace with `std::f64::consts::PI`", """
        const PI: f64 = <warning descr="Approximate value of `std::f64::consts::PI` found. Consider using it directly.">3.1415<caret></warning>;
    """, """
        const PI: f64 = std::f64::consts::PI;
    """)

    fun `test use core when no std in other file`() = checkFixByFileTree("Replace with `core::f64::consts::PI`", """
    //- main.rs
        #![no_std]
        mod foo;
    //- foo.rs
        const PI: f64 = /*warning descr="Approximate value of `core::f64::consts::PI` found. Consider using it directly."*//*caret*/3.1415/*warning**/;
    """, """

    """)
}
