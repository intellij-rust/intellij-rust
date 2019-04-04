/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.rust.ExpandMacros
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyFloat
import org.rust.lang.core.types.ty.TyInteger

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsStdlibExpressionTypeInferenceTest : RsTypificationTestBase() {
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

    fun `test empty vec!`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let mut x = vec![];
            x.push(0u16);
            x;
          //^ Vec<u16>
        }
    """)

    fun `test repeat vec!`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = vec!(1u8; 2usize);
            x;
          //^ Vec<u8>
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

    fun `test env`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = env!("PATH");
            a;
          //^ &str
        }
    """)

    fun `test env 2 args`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = env!("PATH", "PATH env variable not found");
            a;
          //^ &str
        }
    """)

    fun `test option_env`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = option_env!("PATH");
            a;
          //^ Option<&str>
        }
    """)

    fun `test concat`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = concat!("hello", 10);
            a;
          //^ &str
        }
    """)

    fun `test line`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = line!();
            a;
          //^ u32
        }
    """)

    fun `test column`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = column!();
            a;
          //^ u32
        }
    """)

    fun `test file`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = file!();
            a;
          //^ &str
        }
    """)

    fun `test stringify`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = stringify!(1 + 1);
            a;
          //^ &str
        }
    """)

    fun `test include_str`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = include_str!("file.txt");
            a;
          //^ &str
        }
    """)

    fun `test include_bytes`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = include_bytes!("file.txt");
            a;
          //^ &[u8; <unknown>]
        }
    """)

    fun `test module_path`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = module_path!();
            a;
          //^ &str
        }
    """)

    fun `test cfg`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = cfg!(windows);
            a;
          //^ bool
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
                .filter(|x| *x < 30)
                .filter(|x| *x > 10)
                .filter(|x| x % 2 == 0)
                .next().unwrap();
            a;
          //^ i32
        }
    """)

    @ExpandMacros(MacroExpansionScope.ALL, "std")
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

    @ExpandMacros(MacroExpansionScope.ALL, "std")
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

    @ExpandMacros(MacroExpansionScope.ALL, "std")
    fun `test arithmetic op with unconstrained integer`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = 2+2+2;
            a;
          //^ i32
        }
    """)

    @ExpandMacros(MacroExpansionScope.ALL, "std")
    fun `test all arithmetic ops with all numeric types`() {
        TyInteger.NAMES.permutations(ArithmeticOp.values())
            .forEach { (numeric, op) -> doTestBinOp(numeric, op, "0", numeric) }

        val floatOps = listOf(ArithmeticOp.ADD, ArithmeticOp.SUB, ArithmeticOp.MUL, ArithmeticOp.DIV)
        TyFloat.NAMES.permutations(floatOps)
            .forEach { (numeric, op) -> doTestBinOp(numeric, op, "0.0", numeric) }
    }

    @ExpandMacros(MacroExpansionScope.ALL, "std")
    fun `test all arithmetic assignment ops with all numeric types`() {
        TyInteger.NAMES.permutations(ArithmeticAssignmentOp.values())
            .forEach { (numeric, op) -> doTestBinOp(numeric, op, "0", "()") }

        val floatOps = listOf(ArithmeticAssignmentOp.PLUSEQ, ArithmeticAssignmentOp.MINUSEQ,
            ArithmeticAssignmentOp.MULEQ, ArithmeticAssignmentOp.DIVEQ)
        TyFloat.NAMES.permutations(floatOps)
            .forEach { (numeric, op) -> doTestBinOp(numeric, op, "0.0", "()") }
    }

    @ExpandMacros(MacroExpansionScope.ALL, "std")
    fun `test all cmp ops with all numeric types`() {
        val ops: List<OverloadableBinaryOperator> = EqualityOp.values() + ComparisonOp.values()
        TyInteger.NAMES.permutations(ops)
            .forEach { (numeric, op) -> doTestBinOp(numeric, op, "0", "bool") }
        TyFloat.NAMES.permutations(ops)
            .forEach { (numeric, op) -> doTestBinOp(numeric, op, "0.0", "bool") }
    }

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
        op: OverloadableBinaryOperator,
        rhsLiteral: String,
        expectedOutputType: String
    ) {
        val sign = op.sign
        val expectedRhsType = if ((op as BinaryOperator).category == BinOpCategory.Shift) {
            "i32"
        } else {
            lhsType
        }
        stubOnlyTypeInfer("""
            //- main.rs
            fn foo(lhs: $lhsType) {
                let rhs = $rhsLiteral;
                let x = lhs $sign rhs;
                (x, rhs);
              //^ ($expectedOutputType, $expectedRhsType)
            }
        """)
    }

    @ExpandMacros
    fun `test write macro`() = stubOnlyTypeInfer("""
    //- main.rs
        use std::fmt::Write;
        fn main() {
            let mut s = String::new();
            let a = write!(s, "text");
            a;
          //^ Result<(), Error>
        }
    """)

    @ExpandMacros
    fun `test write macro &mut`() = stubOnlyTypeInfer("""
    //- main.rs
        use std::fmt::Write;
        fn main() {
            let mut s = String::new();
            let a = write!(&mut s, "text");
            a;
          //^ Result<(), Error>
        }
    """)

    /** Issue [#2514](https://github.com/intellij-rust/intellij-rust/issues/2514) */
    @ExpandMacros(MacroExpansionScope.ALL, "std")
    fun `test issue 2514`() = stubOnlyTypeInfer("""
    //- main.rs
        struct Foo {
            bar: Box<i32>
        }

        impl Foo {
            fn test(&self) {
                let b = self.bar.as_ref().clone();
                b;
            } //^ i32
        }
    """)

    /** Issue [#2791](https://github.com/intellij-rust/intellij-rust/issues/2791)*/
    fun `test issue 2791`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let mut vec: Vec<i32> = Vec::new();
            for x in &mut vec { x; }
        }                     //^ &mut i32
    """)

    fun `test iterate for impl Iterator type`() = stubOnlyTypeInfer("""
    //- main.rs
        fn foo() -> impl Iterator<Item=u8> { unimplemented!() }
        fn main() {
            for x in foo() { x; }
        }                  //^ u8
    """)

    fun `test box expr`() = stubOnlyTypeInfer("""
    //- main.rs
        struct S;
        fn main() {
            let a = box S;
            a;
        } //^ Box<S>
    """)

    @ExpandMacros(MacroExpansionScope.ALL, "std")
    fun `test iterate "for" complex pattern in complex type (correct type vars resolve)`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            for (a, _) in [(Some(42), false)].iter().map(|(n, f)| (n, f)) {
                a;
            } //^ &Option<i32>
        }
    """)

    @ExpandMacros(MacroExpansionScope.ALL, "std")
    fun `test iterate "while let" complex pattern = complex type (correct type vars resolve)`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            while let Some((a, _)) = [(Some(42), false)].iter().map(|(n, f)| (n, f)).next() {
                a;
            } //^ &Option<i32>
        }
    """)

    @ExpandMacros(MacroExpansionScope.ALL, "std")
    fun `test "if let" complex pattern = complex type (correct type vars resolve)`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            if let Some((a, _)) = [(Some(42), false)].iter().map(|(n, f)| (n, f)).next() {
                a;
            } //^ &Option<i32>
        }
    """)

    fun `test macro impls`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = 0.to_string();
            a;
          //^ String
        }
    """)

    fun `test overloaded add String + &String`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = String::new();
            let b = String::new();
            let c = a + &b;
            c;
        } //^ String
    """)
}
