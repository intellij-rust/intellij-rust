/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

class RsExpressionTypeInferenceTest : RsTypificationTestBase() {
    fun `test function call`() = testExpr("""
        struct S;

        fn new() -> S { S }

        fn main() {
            let x = new();
            x;
          //^ S
        }
    """)

    fun `test unit function call`() = testExpr("""
        fn foo() {}
        fn main() {
            let x = foo();
            x;
          //^ ()
        }
    """)

    fun `test static method call`() = testExpr("""
        struct S;
        struct T;
        impl S { fn new() -> T { T } }

        fn main() {
            let x = S::new();
            x;
          //^ T
        }
    """)

    fun `test block expr`() = testExpr("""
        struct S;

        fn foo() -> S {}
        fn main() {
            let x = { foo() };
            x
          //^ S
        }
    """)

    fun `test unit block expr`() = testExpr("""
        struct S;

        fn foo() -> S {}
        fn main() {
            let x = { foo(); };
            x
          //^ ()
        }
    """)

    fun `test empty block expr`() = testExpr("""
        fn main() {
            let x = {};
            x
          //^ ()
        }
    """)

    fun `test type parameters`() = testExpr("""
        fn foo<FOO>(foo: FOO) {
            let bar = foo;
            bar
          //^ FOO
        }
    """)

    fun `test unit if`() = testExpr("""
        fn main() {
            let x = if true { 92 };
            x
          //^ ()
        }
    """)

    fun `test if`() = testExpr("""
        fn main() {
            let x = if true { 92 } else { 62 };
            x
          //^ i32
        }
    """)

    fun `test if else with return 1`() = testExpr("""
        fn main() {
            let a = if true { return } else { 1 };
            a
        } //^ i32
    """)

    fun `test if else with return 2`() = testExpr("""
        fn main() {
            let a = if true { return } else if true { return } else { 1 };
            a
        } //^ i32
    """)

    fun `test match with return`() = testExpr("""
        fn main() {
            let a = match true {
                true => return,
                false => 1,
            };
            a
        } //^ i32
    """)

    fun `test loop`() = testExpr("""
        fn main() {
            let x = loop { break; };
            x
          //^ ()
        }
    """)

    fun `test endless loop`() = testExpr("""
        fn main() {
            let x = loop { };
            x
          //^ !
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

    fun `test while`() = testExpr("""
        fn main() {
            let x = while false { 92 };
            x
          //^ ()
        }
    """)

    fun `test for`() = testExpr("""
        fn main() {
            let x = for _ in 62..92 {};
            x
          //^ ()
        }
    """)

    fun `test parenthesis`() = testExpr("""
        fn main() {
            (false);
          //^ bool
        }
    """)

    fun `test bool true`() = testExpr("""
        fn main() {
            let a = true;
                     //^ bool
        }
    """)

    fun `test bool false`() = testExpr("""
        fn main() {
            let a = false;
                      //^ bool
        }
    """)

    fun `test char`() = testExpr("""
        fn main() {
            let a = 'A';
                   //^ char
        }
    """)

    fun `test byte char`() = testExpr("""
        fn main() {
            let a = b'A';
                    //^ u8
        }
    """)

    fun `test byte str`() = testExpr("""
        fn main() {
            let a = b"ABC";
                    //^ &[u8; 3]
        }
    """)

    fun `test str ref`() = testExpr("""
        fn main() {
            let a = "Hello";
                       //^ &str
        }
    """)

    fun `test never`() = testExpr("""
        fn never() -> ! { unimplemented!() }
        fn main() {
            let a = never();
            a
        } //^ !
    """)

    fun `test unimplemented macro`() = testExpr("""
        fn main() {
            let a = unimplemented!();
            a
        } //^ !
    """)

    fun `test unreachable macro`() = testExpr("""
        fn main() {
            let a = unreachable!();
            a
        } //^ !
    """)

    fun `test panic macro`() = testExpr("""
        fn main() {
            let a = panic!();
            a
        } //^ !
    """)

    fun `test enum variant A`() = testExpr("""
        enum E { A(i32), B { val: bool }, C }
        fn main() {
            (E::A(92))
          //^ E
        }
    """)

    fun `test enum variant B`() = testExpr("""
        enum E { A(i32), B { val: bool }, C }
        fn main() {
            (E::B { val: false })
          //^ E
        }
    """)

    fun `test enum variant C`() = testExpr("""
        enum E { A(i32), B { val: bool }, C }
        fn main() {
            (E::C)
          //^ E
        }
    """)

    fun `test match expr`() = testExpr("""
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

    fun `test parens`() = testExpr("""
        type T = (i32);
        fn foo(x: T) { x }
                     //^ i32
    """)

    fun `test no stack overflow 1`() = testExpr("""
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

    fun `test no stack overflow 2`() = testExpr("""
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

    fun `test bin operators bool`() {
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

    fun `test cast inner expr`() = testExpr("""
        fn main() {
            let a = 1 as u8;
        }         //^ i32
    """)

    fun `test pointer deref`() = testExpr("""
        fn main() {
            let x : *const i32;
            let y = *x; // it should be in unsafe
            y
          //^ i32
        }
    """)

    fun `test overloaded deref`() = testExpr("""
        #[lang = "deref"]
        trait Deref { type Target; }
        struct A;
        struct B;
        impl Deref for A { type Target = B; }

        fn foo(a: A) {
            let b = *a;
            b
        } //^ B
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

    // TODO: should be [i32; 2]
    fun `test usize constant as array size`() = testExpr("""
        const COUNT: usize = 2;
        fn main() {
            let x = [1; COUNT];
            x
          //^ [i32; <unknown>]
        }
    """)

    fun `test not usize constant as array size`() = testExpr("""
        const COUNT: i32 = 2;
        fn main() {
            let x = [1; COUNT];
            x
          //^ [i32; <unknown>]
        }
    """)

    // TODO: should be [i32; 5]
    fun `test binary expr as array size`() = testExpr("""
        fn main() {
            let x = [1; 2 + 3];
            x
          //^ [i32; <unknown>]
        }
    """)

    fun `test negative binary expr as array size`() = testExpr("""
        fn main() {
            let x = [1; 2 - 3];
            x
          //^ [i32; <unknown>]
        }
    """)

    // TODO: should be [i32; 28]
    fun `test complex expr as array size`() = testExpr("""
        const COUNT: usize = 2;
        fn main() {
            let x = [1; (2 * COUNT + 3) << (4 / 2)];
            x
          //^ [i32; <unknown>]
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

    // https://github.com/intellij-rust/intellij-rust/issues/1584
    fun `test tuple reference field`() = testExpr("""
        fn main() {
            let a: (u32, u32) = (0, 0);
            let v = &a;
            let i = v.1;
            i;
          //^ u32
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

    // https://github.com/intellij-rust/intellij-rust/issues/1549
    fun `test Self type in assoc function`() = testExpr("""
        struct Foo;
        impl Foo {
            fn new() -> Self { unimplemented!() }
        }
        fn main() {
            let x = Foo::new();
            x;
          //^ Foo
        }
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/1549
    fun `test Self type in assoc function with complex ret type`() = testExpr("""
        struct Foo;
        impl Foo {
            fn new_pair() -> (Self, Self) { unimplemented!() }
        }
        fn main() {
            let x = Foo::new_pair();
            x;
          //^ (Foo, Foo)
        }
    """)

    fun `test argument expr of unresolved function`() = testExpr("""
        fn main() {
            foo(1);
        }     //^ i32
    """)

    fun `test excess argument expr of function`() = testExpr("""
        fn foo(a: i32) {}
        fn main() {
            foo(1, 2);
        }        //^ i32
    """)

    fun `test tuple expr with more types than expected`() = testExpr("""
        fn main() {
            let a: (u8,) = (1, 2);
        }                    //^ i32
    """)

    fun `test argument expr of unresolved method`() = testExpr("""
        struct S;
        fn main() {
            S.foo(1);
        }       //^ i32
    """)

    fun `test field expr of unresolved struct`() = testExpr("""
        fn main() {
            S { f: 1 }
        }        //^ i32
    """)

    fun `test unresolved field expr`() = testExpr("""
        struct S;
        fn main() {
            S { f: 1 }
        }        //^ i32
    """)

    fun `test struct with alias`() = testExpr("""
        struct S;
        type T1 = S;
        type T2 = T1;
        fn main() {
            T2 { }
        } //^ S
    """)
    // More struct alias tests in [RsGenericExpressionTypeInferenceTest]

    fun `test index expr of unresolved path`() = testExpr("""
        fn main() {
            a[1]
        }   //^ i32
    """)

    fun `test return inner expr`() = testExpr("""
        fn foo() -> i32 {
            return 1;
        }        //^ i32
    """)

    fun `test loop break inner expr`() = testExpr("""
        fn main() {
            loop {
                break 1;
            }       //^ i32
        }
    """)

    fun `test return expr`() = testExpr("""
        fn main() {
            return;
        } //^ !
    """)

    fun `test loop break expr`() = testExpr("""
        fn main() {
            loop {
                break 1;
            } //^ !
        }
    """)

    fun `test loop continue expr`() = testExpr("""
        fn main() {
            loop {
                continue;
            } //^ !
        }
    """)

    fun `test infer explicit lambda parameter type`() = testExpr("""
        fn main() {
            let a = |x: u8| x;
        }                 //^ u8
    """)

    fun `test infer lambda type with explicit parameters`() = testExpr("""
        fn main() {
            let a = |x: u8| x;
            a
        } //^ fn(u8) -> u8
    """)

    fun `test infer lambda parameters rvalue from lvalue fn pointer`() = testExpr("""
        fn main() {
            let a: fn(u8) = |x| { x; };
        }                       //^ u8
    """)

    fun `test infer excess explicit lambda parameter`() = testExpr("""
        fn main() {
            let a: fn(u8) = |x, y: u8| y;
        }                            //^ u8
    """, allowErrors = true)

    fun `test infer const inside a function`() = testExpr("""
        fn main() {
            const X: i32 = 1;
        }                //^ i32
    """)

    fun `test infer static inside a function`() = testExpr("""
        fn main() {
            static X: i32 = 1;
        }                 //^ i32
    """)

    fun `test unknown type is more priority than '!' type`() = testExpr("""
        fn main() {
            let a = if true { UnknownFoo } else { return };
            a
        } //^ <unknown>
    """)
}
