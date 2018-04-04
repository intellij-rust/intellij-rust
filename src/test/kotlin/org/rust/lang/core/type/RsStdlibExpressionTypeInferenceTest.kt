/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.rust.lang.core.psi.ext.ArithmeticAssignmentOp
import org.rust.lang.core.psi.ext.ArithmeticOp
import org.rust.lang.core.types.ty.TyFloat
import org.rust.lang.core.types.ty.TyInteger

class RsStdlibExpressionTypeInferenceTest : RsTypificationTestBase() {
    override fun getProjectDescriptor() = WithStdlibRustProjectDescriptor

    fun `test RangeFull`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = ..;
            x;
          //^ RangeFull
        }
    """)

    fun `test RangeFull no_std`() = stubOnlyTypeInfer("""
    //- main.rs
        #![no_std]
        fn main() {
            let x = ..;
            x;
          //^ RangeFull
        }
    """)

    fun `test RangeFrom`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = 0u16..;
            x;
          //^ RangeFrom<u16>
        }
    """)

    fun `test RangeTo`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = ..42u16;
            x;
          //^ RangeTo<u16>
        }
    """)

    fun `test Range 2`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = 0u16..42;
            x;
          //^ Range<u16>
        }
    """)

    fun `test Range 1`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = 0..42u16;
            x;
          //^ Range<u16>
        }
    """)

    fun `test RangeToInclusive`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = ...42u16;
            x;
          //^ RangeToInclusive<u16>
        }
    """)

    fun `test RangeToInclusive new syntax`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = ..=42u16;
            x;
          //^ RangeToInclusive<u16>
        }
    """)

    fun `test RangeInclusive 1`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = 0u16...42;
            x;
          //^ RangeInclusive<u16>
        }
    """)

    fun `test RangeInclusive 2`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = 0...42u16;
            x;
          //^ RangeInclusive<u16>
        }
    """)

    fun `test RangeInclusive new syntax`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = 0..=42u16;
            x;
          //^ RangeInclusive<u16>
        }
    """)

    fun `test vec!`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = vec!(1, 2u16, 4, 8);
            x;
          //^ Vec<u16>
        }
    """)

    fun `test vec! no_std`() = stubOnlyTypeInfer("""
    //- main.rs
        #![no_std]
        fn main() {
            let x = vec!(1, 2u16, 4, 8);
            x;
          //^ Vec<u16>
        }
    """)

    fun `test format!`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = format!("{} {}", "Hello", "world!");
            x;
          //^ String
        }
    """)

    fun `test format! no_std`() = stubOnlyTypeInfer("""
    //- main.rs
        #![no_std]
        fn main() {
            let x = format!("{} {}", "Hello", "world!");
            x;
          //^ String
        }
    """)

    fun `test format_args!`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = format_args!("{} {}", "Hello", "world!");
            x;
          //^ Arguments
        }
    """)

    fun `test format_args! no_std`() = stubOnlyTypeInfer("""
    //- main.rs
        #![no_std]
        fn main() {
            let x = format_args!("{} {}", "Hello", "world!");
            x;
          //^ Arguments
        }
    """)

    fun `test assert!`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = assert!(1 != 2);
            x;
          //^ ()
        }
    """)

    fun `test debug_assert!`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = debug_assert!(1 != 2);
            x;
          //^ ()
        }
    """)

    fun `test assert_eq!`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = assert_eq!(1 + 1, 2);
            x;
          //^ ()
        }
    """)

    fun `test assert_ne!`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = assert_ne!(1, 2);
            x;
          //^ ()
        }
    """)

    fun `test debug_assert_eq!`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = debug_assert_eq!(1 + 1, 2);
            x;
          //^ ()
        }
    """)

    fun `test debug_assert_ne!`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = debug_assert_ne!(1, 2);
            x;
          //^ ()
        }
    """)

    fun `test print!`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = print!("Something went wrong");
            x;
          //^ ()
        }
    """)

    fun `test println!`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = println!("Something went wrong");
            x;
          //^ ()
        }
    """)

    fun `test eprint!`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = eprint!("Something went wrong");
            x;
          //^ ()
        }
    """)

    fun `test eprintln!`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = eprintln!("Something went wrong");
            x;
          //^ ()
        }
    """)

    //From the log crate
    fun `test warn!`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = warn!("Something went wrong");
            x;
          //^ ()
        }
    """)

    fun `test infer lambda expr`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let test: Vec<String> = Vec::new();
            test.into_iter().map(|a| a.to_string());
                                   //^ String
        }
    """)

    fun `test infer type of derivable trait method call`() = stubOnlyTypeInfer("""
    //- main.rs
        #[derive(Clone)]
        struct Foo;

        fn bar(foo: Foo) {
            let foo2 = foo.clone();
            foo2;
           //^ Foo
        }
    """)

    fun `test infer iterator map chain`() = stubOnlyTypeInfer("""
    //- main.rs
        struct S<T>(T);
        fn main() {
            let test: Vec<String> = Vec::new();
            let a = test.into_iter()
                .map(|x| x.to_string())
                .map(|x| S(x))
                .map(|x| x.0)
                .next().unwrap();
            a;
          //^ String
        }
    """)

    fun `test infer iterator filter chain`() = stubOnlyTypeInfer("""
    //- main.rs
        struct S<T>(T);
        fn main() {
            let test: Vec<i32> = Vec::new();
            let a = test.into_iter()
                .filter(|x| x < 30)
                .filter(|x| x > 10)
                .filter(|x| x % 2 == 0)
                .next().unwrap();
            a;
          //^ i32
        }
    """)

    fun `test slice iter`() = stubOnlyTypeInfer("""
    //- main.rs
        struct S<T>(T);
        fn main() {
            let test: Vec<i32> = Vec::new();
            let a = test.iter()
                .next().unwrap();
            a;
          //^ &i32
        }
    """)

    fun `test slice iter_mut`() = stubOnlyTypeInfer("""
    //- main.rs
        struct S<T>(T);
        fn main() {
            let mut test: Vec<i32> = Vec::new();
            let a = test.iter_mut()
                .next().unwrap();
            a;
          //^ &mut i32
        }
    """)

    fun `test iterator collect`() = stubOnlyTypeInfer("""
    //- main.rs
        use std::vec::Vec;

        fn main() {
            let vec = vec![1, 2, 3];
            let b: Vec<_> = vec.into_iter().collect();
            b;
          //^ Vec<i32>
        }
    """)

    fun `test iterator collect with path parameter`() = stubOnlyTypeInfer("""
    //- main.rs
        use std::vec::Vec;

        fn main() {
            let vec = vec![1, 2, 3];
            let b = vec.into_iter().collect::<Vec<_>>();
            b;
          //^ Vec<i32>
        }
    """)

    fun `test vec push`() = stubOnlyTypeInfer("""
    //- main.rs
        use std::vec::Vec;

        fn main() {
            let mut vec = Vec::new();
            vec.push(1);
            vec;
          //^ Vec<i32>
        }
    """)

    fun `test vec push 2`() = stubOnlyTypeInfer("""
    //- main.rs
        use std::vec::Vec;

        fn main() {
            let a = 0;
                  //^ u8
            let mut vec = Vec::new();
            vec.push(a);
            vec.push(1u8);
        }
    """)

    fun `test array indexing`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let xs = ["foo", "bar"];
            let x = xs[0];
            x;
          //^ &str
        }
    """)

    fun `test all arithmetic ops with all numeric types`() {
        TyInteger.NAMES.permutations(ArithmeticOp.values().map { it.sign })
            .forEach { (numeric, sign) -> doTestBinOp(numeric, sign, "0", numeric) }
        TyFloat.NAMES.permutations(ArithmeticOp.values().map { it.sign })
            .forEach { (numeric, sign) -> doTestBinOp(numeric, sign, "0.0", numeric) }
    }

    fun `test all arithmetic assignment ops with all numeric types`() {
        TyInteger.NAMES.permutations(ArithmeticAssignmentOp.values().map { it.sign })
            .forEach { (numeric, sign) -> doTestBinOp(numeric, sign, "0", "()") }
        TyFloat.NAMES.permutations(ArithmeticAssignmentOp.values().map { it.sign })
            .forEach { (numeric, sign) -> doTestBinOp(numeric, sign, "0.0", "()") }
    }

    // TODO lang attributes for cmp traits can't be collected correctly doe to `cfg_attr`
    // TODO #[cfg_attr(stage0, lang = "ord")]
//    fun `test all cmp ops with all numeric types`() {
//        TyInteger.NAMES.permutations(ComparisonOp.values().map { it.sign })
//            .forEach { (numeric, sign) -> doTestBinOp(numeric, sign, "0", "bool") }
//        TyFloat.NAMES.permutations(ComparisonOp.values().map { it.sign })
//            .forEach { (numeric, sign) -> doTestBinOp(numeric, sign, "0.0", "bool") }
//    }

    private fun <A, B> Collection<A>.permutations(other: Collection<B>): List<Pair<A, B>> {
        val result = ArrayList<Pair<A, B>>(size*other.size)
        for (a in this) {
            for (b in other) {
                result += Pair(a, b)
            }
        }
        return result
    }

    private fun doTestBinOp(
        lhsType: String,
        sign: String,
        rhsLiteral: String,
        expectedOutputType: String
    ) = stubOnlyTypeInfer("""
        //- main.rs
        fn foo(lhs: $lhsType) {
            let rhs = $rhsLiteral;
            let x = lhs $sign rhs;
            (x, rhs);
          //^ ($expectedOutputType, $lhsType)
        }
    """)
}
