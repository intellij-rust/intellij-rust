/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

class RsPatternMatchingTest : RsTypificationTestBase() {
    fun `test if let pattern`() = testExpr("""
        enum E { L(i32), R(bool) }
        fn main() {
            let _ = if let E::L(x) = E::R(false) { x } else { x };
                                                 //^ i32
        }
    """)

    fun `test while let pattern`() = testExpr("""
        enum E { L(i32), R(bool) }
        fn main() {
            let e = E::L(92);
            while let E::R(x) = e {
                x
            } //^ bool
        }
    """)

    fun `test let type ascription`() = testExpr("""
        struct S;
        struct T;

        fn main() {
            let (x, _): (S, T) = unimplemented!();
            x;
          //^ S
        }
    """)

    fun `test let init expr`() = testExpr("""
        struct S;
        struct T;

        fn main() {
            let (_, x) = (S, T);
            x;
          //^ T
        }
    """)

    fun `test nested struct pattern`() = testExpr("""
        struct S;
        struct T {
            s: S
        }

        fn main() {
            let T { s: x } = T { s: S };
            x;
          //^ S
        }
    """)

    fun `test nested struct ref pattern 1`() = testExpr("""
        struct S;
        struct T {
            s: S
        }

        fn main() {
            let T { s: ref x } = T { s: S };
            x;
          //^ &S
        }
    """)

    fun `test nested struct ref pattern 2`() = testExpr("""
        struct S;
        struct T {
            s: S
        }

        fn main() {
            let T { ref s } = T { s: S };
            s;
          //^ &S
        }
    """)

    fun `test braced enum variant`() = testExpr("""
        enum E { S { foo: i32 }}

        fn main() {
            let x: E = unimplemented!();
            match x { E::S { foo } => foo };
        }                           //^ i32
    """)

    fun `test fn argument pattern`() = testExpr("""
        struct S;
        struct T;

        fn main((x, _): (S, T)) {
            x;
          //^ S
        }
    """)

    fun `test closure argument`() = testExpr("""
        fn main() {
            let _ = |x: ()| {
                x
              //^ ()
            };
        }
    """)

    fun `test ref pattern`() = testExpr("""
        struct Vec;

        fn bar(vr: &Vec) {
            let &v = vr;
            v;
          //^ Vec
        }
    """)

    fun `test ref pattern 2`() = testExpr("""
        struct Vec;

        fn bar(vr: Vec) {
            let ref v = vr;
            v;
          //^ &Vec
        }
    """)

    fun `test ref pattern 3`() = testExpr("""
        struct Vec;

        fn bar(vr: Vec) {
            let ref v = &vr;
            v;
          //^ &&Vec
        }
    """)

    fun `test mut ref pattern`() = testExpr("""
        struct Vec;

        fn bar(vr: &mut Vec) {
            let &v = vr;
            v;
          //^ Vec
        }
    """)

    fun `test mut ref pattern 2`() = testExpr("""
        struct Vec;

        fn bar(vr: Vec) {
            let ref mut v = vr;
            v;
          //^ &mut Vec
        }
    """)

    fun `test tuple out of bounds`() = testExpr("""
        fn main() {
            let (_, _, x) = (1, 2);
            x
          //^ <unknown>
        }
    """)

    fun `test literal pattern`() = testExpr("""
    fn main() {
        let x: (i32, String) = unimplemented!();
        match x { (x, "foo") => x };
    }                         //^ i32
    """)

    fun `test generic tuple struct pattern`() = testExpr("""
        struct S<T>(T);
        fn main() {
            let s = S(123);
            if let S(x) = s { x }
                            //^ i32
        }
    """)

    fun `test generic struct pattern`() = testExpr("""
        struct S<T> { s: T }
        fn main() {
            let s = S { s: 123 };
            match s { S { s: x } => x };
                                  //^ i32
        }
    """)

    fun `test generic enum tuple struct pattern`() = testExpr("""
        enum E<T1, T2> { L(T1), R { r: T2 } }
        fn foo(e: E<i32, bool>) {
            match e {
                E::L(x) => x,
                         //^ i32
                E::R { r: x } => x
            };
        }
    """)

    fun `test generic enum struct pattern`() = testExpr("""
        enum E<T1, T2> { L(T1), R { r: T2 } }
        fn foo(e: E<i32, bool>) {
            match e {
                E::L(x) => x,
                E::R { r: x } => x
                               //^ bool
            };
        }
    """)

    fun `test match ergonomics tuple 1`() = testExpr("""
        fn main() {
            let (a, b) = &(1, 2);
            (a, b);
        } //^ (&i32, &i32)
    """)

    fun `test match ergonomics tuple 2`() = testExpr("""
        fn main() {
            let (a, b) = &mut (1, 2);
            (a, b);
        } //^ (&mut i32, &mut i32)
    """)

    fun `test match ergonomics tuple 3`() = testExpr("""
        fn main() {
            let (a,) = &&(1,);
            a;
        } //^ &i32
    """)

    fun `test match ergonomics tuple 4`() = testExpr("""
        fn main() {
            let (a,) = &mut&(1,);
            a;
        } //^ &i32
    """)

    fun `test match ergonomics tuple 5`() = testExpr("""
        fn main() {
            let (a,) = &&mut(1,);
            a;
        } //^ &i32
    """)

    fun `test match ergonomics tuple 6`() = testExpr("""
        fn main() {
            let (a,) = &mut&mut(1,);
            a;
        } //^ &mut i32
    """)

    fun `test match ergonomics tuple ref`() = testExpr("""
        fn main() {
            let (ref a,) = &(1,);
            a;
        } //^ &i32
    """)

    fun `test match ergonomics enum 1`() = testExpr("""
        enum E { A(i32) }
        fn main() {
            let E::A(a) = &E::A(1);
            a;
        } //^ &i32
    """)

    fun `test match ergonomics enum 2`() = testExpr("""
        enum E { A(i32) }
        fn main() {
            let E::A(ref a) = &E::A(1);
            a;
        } //^ &i32
    """)

    fun `test match ergonomics struct 1`() = testExpr("""
        struct S { a: i32 }
        fn main() {
            let S { a } = &S{ a: 1 };
            a;
        } //^ &i32
    """)

    fun `test match ergonomics struct 2`() = testExpr("""
        struct S { a: i32 }
        fn main() {
            let S { ref a } = &S{ a: 1 };
            a;
        } //^ &i32
    """)

    fun `test match ergonomics struct 3`() = testExpr("""
        struct S { a: i32 }
        fn main() {
            let S { a: ref a } = &S{ a: 1 };
            a;
        } //^ &i32
    """)

    fun `test match ergonomics slice 1`() = testExpr("""
        fn main() {
            let [a, b] = &[1, 2];
            a;
        } //^ &i32
    """)

    fun `test match ergonomics slice 2`() = testExpr("""
        fn main() {
            let [ref a, b] = &[1, 2];
            a;
        } //^ &i32
    """)

    fun `test double ref`() = testExpr("""
        fn main() {
            let ref a = &0;
            a;
        } //^ &&i32
    """)

    fun `test unknown`() = testExpr("""
        fn main() {
            let x = unknown;
            match x {
                Some(n) => {
                    n;
                  //^ <unknown>
                },
                _ => {}
            };
        }
    """)
}
