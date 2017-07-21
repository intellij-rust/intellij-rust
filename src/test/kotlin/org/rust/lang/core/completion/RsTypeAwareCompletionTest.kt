/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsTypeAwareCompletionTest : RsCompletionTestBase() {
    fun `test method call only self`() = doSingleCompletion("""
        struct S;
        impl S { fn frobnicate(self) {} }
        fn main() { S.frob/*caret*/ }
    """, """
        struct S;
        impl S { fn frobnicate(self) {} }
        fn main() { S.frobnicate()/*caret*/ }
    """)

    fun `test method call self and arg`() = doSingleCompletion("""
        struct S;
        impl S { fn frobnicate(self, foo: i32) {} }
        fn main() { S.frob/*caret*/ }
    """, """
        struct S;
        impl S { fn frobnicate(self, foo: i32) {} }
        fn main() { S.frobnicate(/*caret*/) }
    """)

    fun `test don't suggest fields for methods`() = doSingleCompletion("""
        struct S { transmogrificator: i32 }
        impl S { fn transmogrify(&self) {} }

        fn main() {
            let x: S = unimplemented!();
            x.trans/*caret*/()
        }
    """, """
        struct S { transmogrificator: i32 }
        impl S { fn transmogrify(&self) {} }

        fn main() {
            let x: S = unimplemented!();
            x.transmogrify()/*caret*/
        }
    """)

    fun `test method call on &self`() = doSingleCompletion( """
        struct S;

        impl S {
            fn transmogrify(&self) {}

            fn foo(&self) { self.trans/*caret*/ }
        }
    """, """
        struct S;

        impl S {
            fn transmogrify(&self) {}

            fn foo(&self) { self.transmogrify()/*caret*/ }
        }
    """)

    fun `test method call on enum`() = doSingleCompletion("""
        enum E { X }
        impl E { fn quux(&self) {} }

        fn main() {
            let e = E::X;
            e.qu/*caret*/
        }
    """, """
        enum E { X }
        impl E { fn quux(&self) {} }

        fn main() {
            let e = E::X;
            e.quux()/*caret*/
        }
    """)

    fun `test call trait impl for struct method`() = doSingleCompletion("""
        trait SomeTrait { fn some_fn(&self); }
        struct SomeStruct;
        impl SomeTrait for SomeStruct {
            fn some_fn(&self) {}
        }
        fn main() {
            SomeStruct.some_/*caret*/
        }
    """, """
        trait SomeTrait { fn some_fn(&self); }
        struct SomeStruct;
        impl SomeTrait for SomeStruct {
            fn some_fn(&self) {}
        }
        fn main() {
            SomeStruct.some_fn()/*caret*/
        }
    """)

    fun `test call trait impl for enum method`() = doSingleCompletion("""
        trait SomeTrait { fn some_fn(&self); }
        enum SomeEnum { Var1, Var2 }
        impl SomeTrait for SomeEnum {
            fn some_fn(&self) {}
        }
        fn main() {
            let v = SomeEnum::Var1;
            v.some_/*caret*/
        }
    """, """
        trait SomeTrait { fn some_fn(&self); }
        enum SomeEnum { Var1, Var2 }
        impl SomeTrait for SomeEnum {
            fn some_fn(&self) {}
        }
        fn main() {
            let v = SomeEnum::Var1;
            v.some_fn()/*caret*/
        }
    """)

    fun `test field expr`() = doSingleCompletion("""
        struct S { transmogrificator: f32 }

        fn main() {
            let s = S { transmogrificator: 92};
            s.trans/*caret*/
        }
    """, """
        struct S { transmogrificator: f32 }

        fn main() {
            let s = S { transmogrificator: 92};
            s.transmogrificator/*caret*/
        }
    """)

    fun `test static method`() = doSingleCompletion("""
        struct S;
        impl S { fn create() -> S { S } }

        fn main() { let _ = S::cr/*caret*/; }
    """, """
        struct S;
        impl S { fn create() -> S { S } }

        fn main() { let _ = S::create()/*caret*/; }
    """)

    fun `test self method`() = doSingleCompletion("""
        trait Foo {
            fn frobnicate(&self);
            fn bar(&self) { self.frob/*caret*/ }
        }
    """, """
        trait Foo {
            fn frobnicate(&self);
            fn bar(&self) { self.frobnicate()/*caret*/ }
        }
    """)

    fun `test default method`() = doSingleCompletion("""
        trait Frob { fn frobnicate(&self) { } }
        struct S;
        impl Frob for S {}

        fn foo(s: S) { s.frob/*caret*/ }
    """, """
        trait Frob { fn frobnicate(&self) { } }
        struct S;
        impl Frob for S {}

        fn foo(s: S) { s.frobnicate()/*caret*/ }
    """)

    fun `test bound associated type`() = doSingleCompletion("""
        trait Tr { type Item; }
        struct S<A>(A);
        impl<B: Tr> S<B> { fn foo(self) -> B::It/*caret*/ }
    """, """
        trait Tr { type Item; }
        struct S<A>(A);
        impl<B: Tr> S<B> { fn foo(self) -> B::Item/*caret*/ }
    """)

    fun `test bound associated type in explicit UFCS form`() = doSingleCompletion("""
        trait Tr { type Item; }
        struct S<A>(A);
        impl<B: Tr> S<B> { fn foo(self) -> <B as Tr>::It/*caret*/ }
    """, """
        trait Tr { type Item; }
        struct S<A>(A);
        impl<B: Tr> S<B> { fn foo(self) -> <B as Tr>::Item/*caret*/ }
    """)
}
