/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

class RsExpressionTypeInferenceTest : RsTypificationTestBase() {
    fun testFunctionCall() = testExpr("""
        struct S;

        fn new() -> S { S }

        fn main() {
            let x = new();
            x;
          //^ S
        }
    """)

    fun testUnitFunctionCall() = testExpr("""
        fn foo() {}
        fn main() {
            let x = foo();
            x;
          //^ ()
        }
    """)

    fun testStaticMethodCall() = testExpr("""
        struct S;
        struct T;
        impl S { fn new() -> T { T } }

        fn main() {
            let x = S::new();
            x;
          //^ T
        }
    """)

    fun testBlockExpr() = testExpr("""
        struct S;

        fn foo() -> S {}
        fn main() {
            let x = { foo() };
            x
          //^ S
        }
    """)

    fun testUnitBlockExpr() = testExpr("""
        struct S;

        fn foo() -> S {}
        fn main() {
            let x = { foo(); };
            x
          //^ ()
        }
    """)

    fun testEmptyBlockExpr() = testExpr("""
        fn main() {
            let x = {};
            x
          //^ ()
        }
    """)

    fun testTypeParameters() = testExpr("""
        fn foo<FOO>(foo: FOO) {
            let bar = foo;
            bar
          //^ FOO
        }
    """)

    fun testUnitIf() = testExpr("""
        fn main() {
            let x = if true { 92 };
            x
          //^ ()
        }
    """)

    fun testIf() = testExpr("""
        fn main() {
            let x = if true { 92 } else { 62 };
            x
          //^ i32
        }
    """)

    fun `test loop`() = testExpr("""
        fn main() {
            let x = loop { break; };
            x
          //^ ()
        }
    """)

    fun `test loop break value`() = testExpr("""
        fn main() {
            let x = loop { break 7; };
            x
          //^ i32
        }
    """)

    fun `test loop break value in not direct child`() = testExpr("""
        fn foo(v: bool) {
            let x = loop {
                if v { break 7; }
            };
            x
          //^ i32
        }
    """)

    fun `test loop with several breaks`() = testExpr("""
        fn foo(v: bool) {
            let x = loop {
                if v {
                    break 7;
                } else {
                    break 0u32;
                }
            };
            x
          //^ u32
        }
    """)

    fun `test loop with inner loop`() = testExpr("""
        fn main() {
            let x = loop {
                loop { break "bar"; }
                break 7;
            };
            x
          //^ i32
        }
    """)

    fun `test loop labeled break value 1`() = testExpr("""
        fn foo(v: bool) {
            let x = 'outer: loop {
                if v { break 7; }
                for n in 1..10 {
                    if n > 4 {
                        break 'outer 0u32;
                    }
                }
            };
            x
          //^ u32
        }
    """)

    fun `test loop labeled break value 2`() = testExpr("""
        fn foo(v: bool) {
            let x = 'outer: loop {
                let y = loop {
                    if v { break 'outer "bar"; }
                    break 7;
                };
                y
              //^ i32
            };
        }
    """)

    fun `test loop labeled break value 3`() = testExpr("""
        fn foo(v: bool) {
            let x = 'outer: loop {
                break loop {
                    if v { break 'outer 0u32; }
                    break 7;
                }
            };
            x
          //^ u32
        }
    """)

    fun testWhile() = testExpr("""
        fn main() {
            let x = while false { 92 };
            x
          //^ ()
        }
    """)

    fun testFor() = testExpr("""
        fn main() {
            let x = for _ in 62..92 {};
            x
          //^ ()
        }
    """)

    fun testParenthesis() = testExpr("""
        fn main() {
            (false);
          //^ bool
        }
    """)

    fun testDefaultFloat() = testExpr("""
        fn main() {
            let a = 1.0;
                    //^ f64
        }
    """)

    fun testF32() = testExpr("""
        fn main() {
            let a = 1.0f32;
                    //^ f32
        }
    """)

    fun testF64() = testExpr("""
        fn main() {
            let a = 1.0f64;
                    //^ f64
        }
    """)

    fun testDefaultInteger() = testExpr("""
        fn main() {
            let a = 42;
                   //^ i32
        }
    """)

    fun testI8() = testExpr("""
        fn main() {
            let a = 42i8;
                   //^ i8
        }
    """)

    fun testI16() = testExpr("""
        fn main() {
            let a = 42i16;
                   //^ i16
        }
    """)

    fun testI32() = testExpr("""
        fn main() {
            let a = 42i32;
                   //^ i32
        }
    """)

    fun testI64() = testExpr("""
        fn main() {
            let a = 42i64;
                   //^ i64
        }
    """)

    fun testI128() = testExpr("""
        fn main() {
            let a = 42i128;
                   //^ i128
        }
    """)

    fun testISize() = testExpr("""
        fn main() {
            let a = 42isize;
                   //^ isize
        }
    """)

    fun testU8() = testExpr("""
        fn main() {
            let a = 42u8;
                   //^ u8
        }
    """)

    fun testU16() = testExpr("""
        fn main() {
            let a = 42u16;
                   //^ u16
        }
    """)

    fun testU32() = testExpr("""
        fn main() {
            let a = 42u32;
                   //^ u32
        }
    """)

    fun testU64() = testExpr("""
        fn main() {
            let a = 42u64;
                   //^ u64
        }
    """)

    fun testU128() = testExpr("""
        fn main() {
            let a = 42u128;
                   //^ u128
        }
    """)

    fun testUSize() = testExpr("""
        fn main() {
            let a = 42usize;
                   //^ usize
        }
    """)

    fun testBoolTrue() = testExpr("""
        fn main() {
            let a = true;
                     //^ bool
        }
    """)

    fun testBoolFalse() = testExpr("""
        fn main() {
            let a = false;
                      //^ bool
        }
    """)

    fun testChar() = testExpr("""
        fn main() {
            let a = 'A';
                   //^ char
        }
    """)

    fun testStrRef() = testExpr("""
        fn main() {
            let a = "Hello";
                       //^ &str
        }
    """)

    fun testEnumVariantA() = testExpr("""
        enum E { A(i32), B { val: bool }, C }
        fn main() {
            (E::A(92))
          //^ E
        }
    """)

    fun testEnumVariantB() = testExpr("""
        enum E { A(i32), B { val: bool }, C }
        fn main() {
            (E::B { val: 92 })
          //^ E
        }
    """)

    fun testEnumVariantC() = testExpr("""
        enum E { A(i32), B { val: bool }, C }
        fn main() {
            (E::C)
          //^ E
        }
    """)

    fun testMatchExpr() = testExpr("""
        struct S;

        enum E { A(S), B }

        fn foo(e: &E) {
            let s = match *e {
                E::A(ref x) => x,
                E::B => panic!(),
            };
            s
        } //^ &S
    """)

    fun testParens() = testExpr("""
        type T = (i32);
        fn foo(x: T) { x }
                     //^ i32
    """)

    fun testNoStackOverflow1() = testExpr("""
        pub struct P<T: ?Sized> { ptr: Box<T> }

        #[allow(non_snake_case)]
        pub fn P<T: 'static>(value: T) -> P<T> {
            P { ptr: Box::new(value) }
        }

        fn main() {
            let x = P(92);
            x
          //^ P<i32>
        }
    """)

    fun testNoStackOverflow2() = testExpr("""
        fn foo(S: S){
            let x = S;
            x.foo()
              //^ <unknown>
        }
    """)

    fun `test usize in array size`() = testExpr("""
        fn foo() { let _ = [0u8; 10]; }
                                //^ usize
    """)

    fun `test isize in enum variant discriminant`() = testExpr("""
        enum Foo { BAR = 32 }
                        //^ isize
    """)

    fun testBinOperatorsBool() {
        val cases = listOf(
            Pair("1 == 2", "bool"),
            Pair("1 != 2", "bool"),
            Pair("1 <= 2", "bool"),
            Pair("1 >= 2", "bool"),
            Pair("1 < 2", "bool"),
            Pair("1 > 2", "bool"),
            Pair("true && false", "bool"),
            Pair("true || false", "bool")
        )

        for ((i, case) in cases.withIndex()) {
            testExpr("""
                fn main() {
                    let x = ${case.first};
                    x
                  //^ ${case.second}
                }
                """
                ,
                "Case number: $i")
        }
    }

    fun `test assign`() {
        for (op in listOf("=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>="))
            testExpr("""
            fn main() {
                let mut x;
                let y = (x $op 2);
                y
              //^ ()
            }
        """)
    }

    fun `test const pointer`() = testExpr("""
        fn main() {
            let y = 42;
            let x = &y as *const i32;
            x
          //^ *const i32
        }
    """)

    fun `test cast`() = testExpr("""
        fn main() {
            let y = 42;
            let x = &y as i64;
            x
          //^ i64
        }
    """)

    fun `test pointer deref`() = testExpr("""
        fn main() {
            let x : *const i32;
            let y = *x; // it should be in unsafe
            y
          //^ i32
        }
    """)

    fun `test slice type`() = testExpr("""
        fn main() {
            let x : [i32];
            x
          //^ [i32]
        }
    """)

    fun `test array type`() = testExpr("""
        fn main() {
            let x : [i32; 8];
            x
          //^ [i32; 8]
        }
    """)

    fun `test array type2`() = testExpr("""
        fn main() {
            let x : [bool; 8];
            x
          //^ [bool; 8]
        }
    """)

    fun `test array type3`() = testExpr("""
        fn main() {
            let x : [bool; 8usize];
            x
          //^ [bool; 8]
        }
    """)

    fun `test array expression type1`() = testExpr("""
        fn main() {
            let x = [""];
            x
        } //^ [&str; 1]
    """)

    fun `test array expression type2`() = testExpr("""
        fn main() {
            let x = [1, 2, 3];
            x
          //^ [i32; 3]
        }
    """)

    fun `test array expression type3`() = testExpr("""
        fn main() {
            let x = [0; 8];
            x
          //^ [i32; 8]
        }
    """)

    fun `test array expression type4`() = testExpr("""
        fn main() {
            let x = [0; 8usize];
            x
          //^ [i32; 8]
        }
    """)

    fun `test array expression type5`() = testExpr("""
        fn main() {
            let x = [1, 2u16, 3];
            x
          //^ [u16; 3]
        }
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/1269
    fun `test tuple field`() = testExpr("""
        fn main() {
            let x = (1, "foo").1;
            x
          //^ &str
        }
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/1269
    fun `test tuple out of bound field`() = testExpr("""
        fn main() {
            let x = (1, "foo").2;
            x
          //^ <unknown>
        }
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/1423
    fun `test tuple incorrect field`() = testExpr("""
        fn main() {
            let x = (1, "foo").1departure_code;
            x
          //^ <unknown>
        }
    """)


    fun `test associated types for impl`() = testExpr("""
        trait A {
            type Item;
            fn foo(self) -> Self::Item;
        }
        struct S;
        impl A for S {
            type Item = S;
            fn foo(self) -> Self::Item { S }
        }
        fn main() {
            let s = S;
            let a = s.foo();
            a;
        } //^ S
    """)

    fun `test associated types for default impl`() = testExpr("""
        trait A {
            type Item;
            fn foo(self) -> Self::Item { () }
        }
        struct S;
        impl A for S {
            type Item = S;
        }
        fn main() {
            let s = S;
            let a = s.foo();
            a;
        } //^ S
    """)
}
