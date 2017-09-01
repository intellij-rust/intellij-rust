/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.rust.lang.core.psi.ext.ArithmeticOp

class RsStdlibExpressionTypeInferenceTest : RsTypificationTestBase() {
    override fun getProjectDescriptor() = WithStdlibRustProjectDescriptor

    fun `test RangeFull`() = testExpr("""
        fn main() {
            let x = ..;
            x
          //^ RangeFull
        }
    """)

    fun `test RangeFull no_std`() = testExpr("""
        #![no_std]
        fn main() {
            let x = ..;
            x
          //^ RangeFull
        }
    """)

    fun `test RangeFrom`() = testExpr("""
        fn main() {
            let x = 0u16..;
            x
          //^ RangeFrom<u16>
        }
    """)

    fun `test RangeTo`() = testExpr("""
        fn main() {
            let x = ..42u16;
            x
          //^ RangeTo<u16>
        }
    """)

    fun `test Range 2`() = testExpr("""
        fn main() {
            let x = 0u16..42;
            x
          //^ Range<u16>
        }
    """)

    fun `test Range 1`() = testExpr("""
        fn main() {
            let x = 0..42u16;
            x
          //^ Range<u16>
        }
    """)

    fun `test RangeToInclusive`() = testExpr("""
        fn main() {
            let x = ...42u16;
            x
          //^ RangeToInclusive<u16>
        }
    """)

    fun `test RangeInclusive 1`() = testExpr("""
        fn main() {
            let x = 0u16...42;
            x
          //^ RangeInclusive<u16>
        }
    """)

    fun `test RangeInclusive 2`() = testExpr("""
        fn main() {
            let x = 0...42u16;
            x
          //^ RangeInclusive<u16>
        }
    """)

    fun `test vec!`() = testExpr("""
        fn main() {
            let x = vec!(1, 2u16, 4, 8);
            x
          //^ Vec<u16>
        }
    """)

    fun `test vec! no_std`() = testExpr("""
        #![no_std]
        fn main() {
            let x = vec!(1, 2u16, 4, 8);
            x
          //^ Vec<u16>
        }
    """)

    fun `test format!`() = testExpr("""
        fn main() {
            let x = format!("{} {}", "Hello", "world!");
            x
          //^ String
        }
    """)

    fun `test format! no_std`() = testExpr("""
        #![no_std]
        fn main() {
            let x = format!("{} {}", "Hello", "world!");
            x
          //^ String
        }
    """)

    fun `test format_args!`() = testExpr("""
        fn main() {
            let x = format_args!("{} {}", "Hello", "world!");
            x
          //^ Arguments
        }
    """)

    fun `test format_args! no_std`() = testExpr("""
        #![no_std]
        fn main() {
            let x = format_args!("{} {}", "Hello", "world!");
            x
          //^ Arguments
        }
    """)

    fun `test assert!`() = testExpr("""
        fn main() {
            let x = assert!(1 != 2);
            x
          //^ ()
        }
    """)

    fun `test debug_assert!`() = testExpr("""
        fn main() {
            let x = debug_assert!(1 != 2);
            x
          //^ ()
        }
    """)

    fun `test assert_eq!`() = testExpr("""
        fn main() {
            let x = assert_eq!(1 + 1, 2);
            x
          //^ ()
        }
    """)

    fun `test assert_ne!`() = testExpr("""
        fn main() {
            let x = assert_ne!(1, 2);
            x
          //^ ()
        }
    """)

    fun `test debug_assert_eq!`() = testExpr("""
        fn main() {
            let x = debug_assert_eq!(1 + 1, 2);
            x
          //^ ()
        }
    """)

    fun `test debug_assert_ne!`() = testExpr("""
        fn main() {
            let x = debug_assert_ne!(1, 2);
            x
          //^ ()
        }
    """)

    fun `test print!`() = testExpr("""
        fn main() {
            let x = print!("Something went wrong");
            x
          //^ ()
        }
    """)

    fun `test println!`() = testExpr("""
        fn main() {
            let x = println!("Something went wrong");
            x
          //^ ()
        }
    """)

    fun `test eprint!`() = testExpr("""
        fn main() {
            let x = eprint!("Something went wrong");
            x
          //^ ()
        }
    """)

    fun `test eprintln!`() = testExpr("""
        fn main() {
            let x = eprintln!("Something went wrong");
            x
          //^ ()
        }
    """)

    //From the log crate
    fun `test warn!`() = testExpr("""
        fn main() {
            let x = warn!("Something went wrong");
            x
          //^ ()
        }
    """)

    fun `test infer lambda expr`() = testExpr("""
        fn main() {
            let test: Vec<String> = Vec::new();
            test.into_iter().map(|a| a.to_string());
                                   //^ String
        }
    """)

    fun `test infer type of derivable trait method call`() = testExpr("""
        #[derive(Clone)]
        struct Foo;

        fn bar(foo: Foo) {
            let foo2 = foo.clone();
            foo2
           //^ Foo
        }
    """)

    fun `test infer iterator map chain`() = testExpr("""
        struct S<T>(T);
        fn main() {
            let test: Vec<String> = Vec::new();
            let a = test.into_iter()
                .map(|x| x.to_string())
                .map(|x| S(x))
                .map(|x| x.0)
                .next().unwrap();
            a
          //^ String
        }
    """)

    fun `test infer iterator filter chain`() = testExpr("""
        struct S<T>(T);
        fn main() {
            let test: Vec<i32> = Vec::new();
            let a = test.into_iter()
                .filter(|x| x < 30)
                .filter(|x| x > 10)
                .filter(|x| x % 2 == 0)
                .next().unwrap();
            a
          //^ i32
        }
    """)

    fun `test slice iter`() = testExpr("""
        struct S<T>(T);
        fn main() {
            let test: Vec<i32> = Vec::new();
            let a = test.iter()
                .next().unwrap();
            a
          //^ &i32
        }
    """)

    fun `test slice iter_mut`() = testExpr("""
        struct S<T>(T);
        fn main() {
            let mut test: Vec<i32> = Vec::new();
            let a = test.iter_mut()
                .next().unwrap();
            a
          //^ &mut i32
        }
    """)

    fun `test all binary ops with all numeric types`() {
        val numericTypes = listOf(
            "usize", "u8", "u16", "u32", "u64", "u128",
            "isize", "i8", "i16", "i32", "i64", "i128",
            "f32", "f64"
        )

        for (numeric in numericTypes) {
            for ((_, _, sign) in ArithmeticOp.values()) {
                testExpr("""
                    fn foo(lhs: $numeric, rhs: $numeric) {
                        let x = lhs $sign rhs;
                        x
                      //^ $numeric
                    }
                """)
            }
        }
    }
}
