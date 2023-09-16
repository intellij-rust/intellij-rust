/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.rust.MinRustcVersion
import org.rust.ProjectDescriptor
import org.rust.WithStdlibAndStdlibLikeDependencyRustProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
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
          //^ Vec<u16> | Vec<u16, Global>
        }
    """)

    @ProjectDescriptor(WithStdlibAndStdlibLikeDependencyRustProjectDescriptor::class)
    fun `test vec! with stdlib-like dependencies`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = vec!(1, 2u16, 4, 8);
            x;
          //^ Vec<u16> | Vec<u16, Global>
        }
    """)

    fun `test vec! no_std`() = stubOnlyTypeInfer("""
    //- main.rs
        #![no_std]
        fn main() {
            let x = vec!(1, 2u16, 4, 8);
            x;
          //^ Vec<u16> | Vec<u16, Global>
        }
    """)

    fun `test empty vec!`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let mut x = vec![];
            x.push(0u16);
            x;
          //^ Vec<u16> | Vec<u16, Global>
        }
    """)

    fun `test repeat vec!`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let x = vec!(1u8; 2usize);
            x;
          //^ Vec<u8> | Vec<u8, Global>
        }
    """)

    fun `test custom vec!`() = stubOnlyTypeInfer("""
    //- main.rs
        macro_rules! vec {
            ($ name:ident [ $($ field:ident = $ index:expr),* ] = $ fixed:ty) => {  };
        }
        fn main() {
            let x = vec!(Vector2 [x=0, y=1] = [T; 2]);
            x;
          //^ <unknown>
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

    fun `test custom format!`() = stubOnlyTypeInfer("""
    //- main.rs
        macro_rules! format {
            ($ name:ident [ $($ field:ident = $ index:expr),* ] = $ fixed:ty) => {  };
        }
        fn main() {
            let x = format!(Vector2 [x=0, y=1] = [T; 2]);
            x;
          //^ <unknown>
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

    fun `test custom assert!`() = stubOnlyTypeInfer("""
    //- main.rs
        macro_rules! assert {
            ($ name:ident [ $($ field:ident = $ index:expr),* ] = $ fixed:ty) => {  };
        }
        fn main() {
            let x = assert!(Vector2 [x=0, y=1] = [T; 2]);
            x;
          //^ <unknown>
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
        #[derive(PartialOrd, Ord, PartialEq, Eq)]
        enum Level {
            Warn
        }

        const STATIC_MAX_LEVEL: Level = Level::Warn;

        fn max_level() -> Level { unimplemented!() }

        fn __private_api_log(
            args: std::fmt::Arguments,
            level: Level,
            &(target, module_path, file, line): &(&str, &'static str, &'static str, u32),
        ) {}

        #[macro_export]
        macro_rules! __log_format_args {
            (${'$'}($ args:tt)*) => {
                format_args!(${'$'}($ args)*)
            };
        }

        #[macro_export(local_inner_macros)]
        macro_rules! log {
            (target: $ target:expr, $ lvl:expr, ${'$'}($ arg:tt)+) => ({
                let lvl = $ lvl;
                if lvl <= STATIC_MAX_LEVEL && lvl <= max_level() {
                    __private_api_log(
                        __log_format_args!(${'$'}($ arg)+),
                        lvl,
                        &($ target, std::module_path!(), std::file!(), std::line!()),
                    );
                }
            });
            ($ lvl:expr, ${'$'}($ arg:tt)+) => (log!(target: std::module_path!(), $ lvl, ${'$'}($ arg)+))
        }

        #[macro_export(local_inner_macros)]
        macro_rules! warn {
            (target: ${'$'} target:expr, ${'$'}(${'$'} arg:tt)+) => (
                log!(target: ${'$'} target, Level::Warn, ${'$'}(${'$'} arg)+)
            );
            (${'$'}(${'$'} arg:tt)+) => (
                log!(Level::Warn, ${'$'}(${'$'} arg)+)
            )
        }

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

    fun `test iter take`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let mut names = vec![0i32, 1]
                .iter()
                .take(3)
                .next();
            names;
          //^ Option<&i32>
        }
    """)

    fun `test iterator collect`() = stubOnlyTypeInfer("""
    //- main.rs
        use std::vec::Vec;

        fn main() {
            let vec = vec![1, 2, 3];
            let b: Vec<_> = vec.into_iter().collect();
            b;
          //^ Vec<i32> | Vec<i32, Global>
        }
    """)

    fun `test iterator collect with path parameter`() = stubOnlyTypeInfer("""
    //- main.rs
        use std::vec::Vec;

        fn main() {
            let vec = vec![1, 2, 3];
            let b = vec.into_iter().collect::<Vec<_>>();
            b;
          //^ Vec<i32> | Vec<i32, Global>
        }
    """)

    fun `test iterator cloned`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = vec![1, 2].iter()
                .cloned()
                .next();
            a;
        } //^ Option<i32>
    """)

    fun `test vec push`() = stubOnlyTypeInfer("""
    //- main.rs
        use std::vec::Vec;

        fn main() {
            let mut vec = Vec::new();
            vec.push(1);
            vec;
          //^ Vec<i32> | Vec<i32, Global>
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

    fun `test arithmetic op with unconstrained integer`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = 2+2+2;
            a;
          //^ i32
        }
    """)

    fun `test all arithmetic ops with all numeric types`() {
        TyInteger.NAMES.permutations(ArithmeticOp.values())
            .forEach { (numeric, op) -> doTestBinOp(numeric, op, "0", numeric) }

        val floatOps = listOf(ArithmeticOp.ADD, ArithmeticOp.SUB, ArithmeticOp.MUL, ArithmeticOp.DIV)
        TyFloat.NAMES.permutations(floatOps)
            .forEach { (numeric, op) -> doTestBinOp(numeric, op, "0.0", numeric) }
    }

    fun `test all arithmetic assignment ops with all numeric types`() {
        TyInteger.NAMES.permutations(ArithmeticAssignmentOp.values())
            .forEach { (numeric, op) -> doTestBinOp(numeric, op, "0", "()") }

        val floatOps = listOf(ArithmeticAssignmentOp.PLUSEQ, ArithmeticAssignmentOp.MINUSEQ,
            ArithmeticAssignmentOp.MULEQ, ArithmeticAssignmentOp.DIVEQ)
        TyFloat.NAMES.permutations(floatOps)
            .forEach { (numeric, op) -> doTestBinOp(numeric, op, "0.0", "()") }
    }

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
        } //^ Box<S> | Box<S, Global>
    """)

    fun `test iterate 'for' complex pattern in complex type (correct type vars resolve)`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            for (a, _) in [(Some(42), false)].iter().map(|(n, f)| (n, f)) {
                a;
            } //^ &Option<i32>
        }
    """)

    fun `test iterate 'while let' complex pattern = complex type (correct type vars resolve)`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            while let Some((a, _)) = [(Some(42), false)].iter().map(|(n, f)| (n, f)).next() {
                a;
            } //^ &Option<i32>
        }
    """)

    fun `test 'if let' complex pattern = complex type (correct type vars resolve)`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            if let Some((a, _)) = [(Some(42), false)].iter().map(|(n, f)| (n, f)).next() {
                a;
            } //^ &Option<i32>
        }
    """)

    fun `test 'try expr' on a complex type = complex type (correct type vars resolve)`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = vec![Some(42)].into_iter().next()??;
            a;
        } //^ i32
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

    fun `test 'Clone' is not derived for generic type`() = stubOnlyTypeInfer("""
    //- main.rs
        struct X; // Not `Clone`
        #[derive(Clone)]
        struct S<T>(T);
        fn main() {
            let a = &S(X);
            let b = a.clone(); // `S<X>` is not `Clone`, so resolved to std `impl for &T`
            b;
        } //^ &S<X>
    """)

    fun `test await pin future`() = stubOnlyTypeInfer("""
    //- main.rs
        use std::future::Future;
        use std::pin::Pin;
        async fn foo<T: Future<Output=i32>>(p: Pin<&mut T>) {
            let a = p.await;
            a;
        } //^ i32
    """)

    // There is `impl<T: ?Sized> !Clone for &mut T {}`
    fun `test clone through mut ref`() = stubOnlyTypeInfer("""
    //- main.rs
        #[derive(Clone)]
        pub struct S;

        fn main() {
            let a = &mut S;
            let b = a.clone();
            b;
        } //^ S
    """)

    // Issue https://github.com/intellij-rust/intellij-rust/issues/6749
    fun `test diverging and non-diverging match arm`() = stubOnlyTypeInfer("""
    //- main.rs
        fn foo(x: i32) {}

        fn main() {
            let num2 = match "42".parse() {
                Ok(v) => v,
                Err(e) => panic!(),
            }; // type of `num2` should be `i32`, not `!`
            foo(num2);
            num2;
        } //^ i32
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/8405
    @MinRustcVersion("1.51.0")
    fun `test addr_of_mut!`() = stubOnlyTypeInfer("""
    //- main.rs
        use std::ptr::addr_of_mut;
        fn main() {
            let mut a = 123;
            let b = addr_of_mut!(a);
            b;
          //^ *mut i32
        }
    """)

    fun `test iter map using std identity`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = vec![1, 2]
                .into_iter()
                .map(std::convert::identity)
                .next()
                .unwrap();
            a;
        } //^ i32
    """)

    fun `test call an fn pointer reference`() = stubOnlyTypeInfer("""
    //- main.rs
        fn foo(f: &fn() -> i32) {
            let a = f();
            a;
        } //^ i32
    """)

    fun `test call an fn pointer 2-reference`() = stubOnlyTypeInfer("""
    //- main.rs
        fn foo(f: &&fn() -> i32) {
            let a = f();
            a;
        } //^ i32
    """)

    fun `test call an fn pointer under Rc`() = stubOnlyTypeInfer("""
    //- main.rs
        use std::rc::Rc;
        fn foo(f: Rc<fn() -> i32>) {
            let a = f();
            a;
        } //^ i32
    """)

    fun `test call type parameter with FnOnce with implicit return type`() = stubOnlyTypeInfer("""
    //- main.rs
        pub fn foo<F: FnOnce(i32)>(f: F) -> Self {
            let a = f(1);
            a;
        } //^ ()
    """)

    fun `test box unsizing`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            foo(Box::new([1, 2, 3]));
        }             //^ Box<[u8; 3], Global>|Box<[u8; 3]>

        fn foo(a: Box<[u8]>) {}
    """)

    fun `test infer lambda parameter type 1`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = |b| {
                b;
            };//^ i32
            foo(a);
        }

        fn foo(_: fn(i32)) {}
    """)

    fun `test infer lambda parameter type 2`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = |b| {
                b;
            };//^ i32
            foo(a);
        }

        fn foo<T: FnOnce(i32)>(_: T) {}
    """)

    fun `test try-poll`() = stubOnlyTypeInfer("""
    //- main.rs
        use std::task::Poll;
        fn foo(p: Poll<Result<i32, ()>>) -> Result<(), ()> {
            let a = p?;
            a;
          //^ Poll<i32>
            Ok(())
        }
    """)

    fun `test for loop over type parameter implementing Iterator`() = stubOnlyTypeInfer("""
    //- main.rs
        fn foo<I: Iterator<Item = i32>>(a: I) {
            for b in a {
                b;
            } //^ i32
        }
    """)

    fun `test Option clone method`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = Some(String::new());
            let b = a.clone();
            b;
        } //^ Option<String>
    """)

    fun `test todo macro`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = todo!();
            a;
        } //^ !
    """)

    fun `test cstr`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = c"foo";
            a;
        } //^ &CStr
    """)

    fun `test raw cstr`() = stubOnlyTypeInfer("""
    //- main.rs
        fn main() {
            let a = cr#"foo"#;
            a;
        } //^ &CStr
    """)

    fun `test custom macros with stdlib names`() {
        // name -> argument count
        val macros = mapOf(
            "dbg" to 1,
            "format" to 1,
            "format_args" to 1,
            "format_args_nl" to 1,
            "write" to 1,
            "writeln" to 1,
            "print" to 1,
            "println" to 1,
            "eprint" to 1,
            "eprintln" to 1,
            "panic" to 1,
            "unimplemented" to 1,
            "unreachable" to 1,
            "todo" to 1,
            "assert" to 1,
            "debug_assert" to 1,
            "assert_eq" to 2,
            "assert_ne" to 2,
            "debug_assert_eq" to 2,
            "debug_assert_ne" to 2,
            "vec" to 1,
            "include_str" to 1,
            "include_bytes" to 1,
            // `MacroExpansionManagerImpl.getExpansionFor` always processes `include!` call as if `include!` is from stdlib.
            // As a result, the expansion is null in this test and the plugin can't infer the proper type.
            //
            //"include" to 1,
            "concat" to 1,
            "env" to 1,
            "option_env" to 1,
            "line" to 0,
            "column" to 0,
            "file" to 0,
            "stringify" to 1,
            "cfg" to 1,
            "module_path" to 0
        )

        for ((name, argsCount) in macros) {
            val argsDefinition = (0..argsCount).joinToString(", ") { "$ x$it:expr" }
            val args = (0..argsCount).joinToString(", ") { "\"it\"" }
            // We try to construct custom macro definition in a way to trigger the same parser rules
            // as if it would be a macro from stdlib
            stubOnlyTypeInfer("""
            //- main.rs
                struct S;
                macro_rules! $name {
                    ($argsDefinition) => { S };
                }

                fn main() {
                    let a = $name!($args);
                    a;
                  //^ S
                }
            """, description = "Macro `$name`")
        }
    }
}
