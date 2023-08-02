/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.inspections.RsInspectionsTestBase

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)// for arithmetic type inference
class RsReplaceCastWithSuffixInspectionTest : RsInspectionsTestBase(RsReplaceCastWithSuffixInspection::class) {

    fun `test integer cast`() = checkWarnings("""
        fn main() {
            let a = /*weak_warning descr="Can be replaced with literal suffix"*/1 as i8/*weak_warning**/;
            let b = /*weak_warning descr="Can be replaced with literal suffix"*/1 as i16/*weak_warning**/;
            let c = /*weak_warning descr="Can be replaced with literal suffix"*/1 as i32/*weak_warning**/;
            let d = /*weak_warning descr="Can be replaced with literal suffix"*/1 as i64/*weak_warning**/;
            let e = /*weak_warning descr="Can be replaced with literal suffix"*/1 as i128/*weak_warning**/;

            let a = /*weak_warning descr="Can be replaced with literal suffix"*/1 as u8/*weak_warning**/;
            let b = /*weak_warning descr="Can be replaced with literal suffix"*/1 as u16/*weak_warning**/;
            let c = /*weak_warning descr="Can be replaced with literal suffix"*/1 as u32/*weak_warning**/;
            let d = /*weak_warning descr="Can be replaced with literal suffix"*/1 as u64/*weak_warning**/;
            let e = /*weak_warning descr="Can be replaced with literal suffix"*/1 as u128/*weak_warning**/;

            let a = /*weak_warning descr="Can be replaced with literal suffix"*/1 as isize/*weak_warning**/;
            let b = /*weak_warning descr="Can be replaced with literal suffix"*/1 as usize/*weak_warning**/;
        }
    """)

    fun `test negative integer cast`() = checkWarnings("""
        fn main() {
            let a = /*weak_warning descr="Can be replaced with literal suffix"*/-1 as i8/*weak_warning**/;
            let b = /*weak_warning descr="Can be replaced with literal suffix"*/-1 as i16/*weak_warning**/;
            let c = /*weak_warning descr="Can be replaced with literal suffix"*/-1 as i32/*weak_warning**/;
            let d = /*weak_warning descr="Can be replaced with literal suffix"*/-1 as i64/*weak_warning**/;
            let e = /*weak_warning descr="Can be replaced with literal suffix"*/-1 as i128/*weak_warning**/;
            let f = /*weak_warning descr="Can be replaced with literal suffix"*/-1 as isize/*weak_warning**/;

            let a = /*weak_warning descr="Can be replaced with literal suffix"*/-1 as u8/*weak_warning**/;
            let b = /*weak_warning descr="Can be replaced with literal suffix"*/-1 as u16/*weak_warning**/;
            let c = /*weak_warning descr="Can be replaced with literal suffix"*/-1 as u32/*weak_warning**/;
            let d = /*weak_warning descr="Can be replaced with literal suffix"*/-1 as u64/*weak_warning**/;
            let e = /*weak_warning descr="Can be replaced with literal suffix"*/-1 as u128/*weak_warning**/;

            let a = /*weak_warning descr="Can be replaced with literal suffix"*/-1 as isize/*weak_warning**/;
            let b = /*weak_warning descr="Can be replaced with literal suffix"*/-1 as usize/*weak_warning**/;
        }
    """)

    fun `test float cast`() = checkWarnings("""
        fn main() {
            let a = /*weak_warning descr="Can be replaced with literal suffix"*/1.0 as f32/*weak_warning**/;
            let b = /*weak_warning descr="Can be replaced with literal suffix"*/1.0 as f64/*weak_warning**/;
        }
    """)

    fun `test negative float cast`() = checkWarnings("""
        fn main() {
            let a = /*weak_warning descr="Can be replaced with literal suffix"*/-1.0 as f32/*weak_warning**/;
            let b = /*weak_warning descr="Can be replaced with literal suffix"*/-1.0 as f64/*weak_warning**/;
        }
    """)


    fun `test integer as float cast`() = checkWarnings("""
        fn main() {
            let a = /*weak_warning descr="Can be replaced with literal suffix"*/1 as f32/*weak_warning**/;
            let b = /*weak_warning descr="Can be replaced with literal suffix"*/1 as f64/*weak_warning**/;
            let c = /*weak_warning descr="Can be replaced with literal suffix"*/-1 as f32/*weak_warning**/;
            let d = /*weak_warning descr="Can be replaced with literal suffix"*/-1 as f64/*weak_warning**/;
        }
    """)


    fun `test non-decimal numbers`() = checkWarnings("""
        fn main() {
            let a = /*weak_warning descr="Can be replaced with literal suffix"*/0b111 as i8/*weak_warning**/;
            let b = /*weak_warning descr="Can be replaced with literal suffix"*/0xff as i32/*weak_warning**/;
            let c = /*weak_warning descr="Can be replaced with literal suffix"*/0o77 as i64/*weak_warning**/;
        }
    """)

    fun `test with suffix cast`() = checkWarnings("""
        fn main() {
            let a = 1.0f32 as f32;
            let b = 1.0f64 as f64;
            let c = 1.0f64 as f32;
            let d = 1.0f32 as f64;

            let a = 1i32 as i32;
            let b = 1i16 as i32;
            let c = 1i64 as i16;
            let d = 1u64 as u64;
            let e = 1usize as usize;
            let f = 1usize as isize;
            let g = 1isize as isize;
            let h = 1isize as usize;

            let a = -1.0f32 as f32;
            let b = -1.0f64 as f64;
            let c = -1.0f64 as f32;
            let d = -1.0f32 as f64;

            let a = -1i32 as i32;
            let b = -1i16 as i32;
            let c = -1i64 as i16;
            let d = -1u64 as u64;
            let e = -1usize as usize;
            let f = -1usize as isize;
            let g = -1isize as isize;
            let h = -1isize as usize;
        }
    """)

    fun `test non-number primitives`() = checkWarnings("""
        fn main() {
            let a = true as bool;
            let b = 1 as bool;
            let c = 1 as char;
            let d = &1 as &i32;
            let e = b'A' as u8;
            let f = "1" as u32;
        }
    """)

    fun `test type alias`() = checkWarnings("""
        type A = i32;
        fn main() {
            let a = 1 as A;
        }
    """)

    fun `test associated type`() = checkWarnings("""
        pub trait T {
            type Item;
        }

        struct S;

        impl T for S {
            type Item = i32;
        }

        fn main() {
            let a = 1 as <S as T>::Item;
        }
    """)

    fun `test non-primitive expr`() = checkWarnings("""
        fn main() {
            let a = if 1 + 1 == 2 { 1 } else { 2i32 } as i64;
        }
    """)

    fun `test not allowed suffixes`() = checkWarnings("""
        fn main() {
            let a = 0o73 as f64;
            let b = 11.0 as i32;
            let c = 11.0 as i64;
            let d = 11.0 as usize;
            let e = 0b11 as f32;
            let f = 0xff as f64;
        }
    """)
}
