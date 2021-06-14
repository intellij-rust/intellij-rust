/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class SubstituteTypeAliasIntentionTest : RsIntentionTestBase(SubstituteTypeAliasIntention::class) {
    fun `test unavailable on trait associated type`() = doUnavailableTest("""
        trait Trait {
            type Item;
        }
        fn foo<T: Trait>() -> T::/*caret*/Item {
            unimplemented!()
        }
    """)

    fun `test trait associated type with a default`() = doAvailableTest("""
        trait Trait {
            type Item = u32;
        }
        fn foo<T: Trait>() -> T::/*caret*/Item {
            unimplemented!()
        }
    """, """
        trait Trait {
            type Item = u32;
        }
        fn foo<T: Trait>() -> u32 {
            unimplemented!()
        }
    """)

    fun `test associated type in type context`() = doAvailableTest("""
        trait Trait {
            type Item;
            fn foo(&self) -> Self::Item;
        }
        impl Trait for () {
            type Item = i32;

            fn foo(&self) -> <Self as Trait>::/*caret*/Item {
                unimplemented!()
            }
        }
    """, """
        trait Trait {
            type Item;
            fn foo(&self) -> Self::Item;
        }
        impl Trait for () {
            type Item = i32;

            fn foo(&self) -> i32 {
                unimplemented!()
            }
        }
    """)

    fun `test associated type in type context with type parameters`() = doAvailableTest("""
        struct S<R>(R);
        trait Trait<T> {
            type Item;
            fn foo(&self, item: Self::Item) -> T;
        }
        impl<T> Trait<T> for () {
            type Item = S<T>;

            fn foo(&self, item: Self::/*caret*/Item) -> T {
                unimplemented!()
            }
        }
    """, """
        struct S<R>(R);
        trait Trait<T> {
            type Item;
            fn foo(&self, item: Self::Item) -> T;
        }
        impl<T> Trait<T> for () {
            type Item = S<T>;

            fn foo(&self, item: S<T>) -> T {
                unimplemented!()
            }
        }
    """)

    fun `test associated type in expression context`() = doAvailableTest("""
        struct S;
        impl S {
            fn bar() {}
        }
        trait Trait {
            type Item;
            fn foo(&self);
        }
        impl Trait for () {
            type Item = S;

            fn foo(&self) {
                <Self as Trait>::/*caret*/Item::bar();
            }
        }
    """, """
        struct S;
        impl S {
            fn bar() {}
        }
        trait Trait {
            type Item;
            fn foo(&self);
        }
        impl Trait for () {
            type Item = S;

            fn foo(&self) {
                S::bar();
            }
        }
    """)

    fun `test associated type in expression context with type parameters`() = doAvailableTest("""
        struct S<R>(R);
        impl<R> S<R> {
            fn bar() {}
        }
        trait Trait {
            type Item;
            fn foo(&self);
        }
        impl Trait for () {
            type Item = S<u32>;

            fn foo(&self) {
                <Self as Trait>::/*caret*/Item::bar();
            }
        }
    """, """
        struct S<R>(R);
        impl<R> S<R> {
            fn bar() {}
        }
        trait Trait {
            type Item;
            fn foo(&self);
        }
        impl Trait for () {
            type Item = S<u32>;

            fn foo(&self) {
                S::<u32>::bar();
            }
        }
    """)

    fun `test associated type in expression context with type qual`() = doAvailableTest("""
        struct S<R>(R);
        impl<R> S<R> {
            fn bar() {}
        }
        impl Trait for S<u32> {
            type Item = Self;
            fn foo(&self) {}
        }

        trait Trait {
            type Item;
            fn foo(&self);
        }
        impl Trait for () {
            type Item = <S<u32> as Trait>::Item;

            fn foo(&self) {
                <Self as Trait>::/*caret*/Item::bar();
            }
        }
    """, """
        struct S<R>(R);
        impl<R> S<R> {
            fn bar() {}
        }
        impl Trait for S<u32> {
            type Item = Self;
            fn foo(&self) {}
        }

        trait Trait {
            type Item;
            fn foo(&self);
        }
        impl Trait for () {
            type Item = <S<u32> as Trait>::Item;

            fn foo(&self) {
                <S<u32> as Trait>::Item::bar();
            }
        }
    """)

    fun `test import after substitution`() = doAvailableTest("""
        mod foo {
            pub struct S;
            pub trait Trait {
                type Item = S;
            }
        }
        fn foo<T: foo::Trait>() -> T::/*caret*/Item {
            unimplemented!()
        }
    """, """
        use foo::S;

        mod foo {
            pub struct S;
            pub trait Trait {
                type Item = S;
            }
        }
        fn foo<T: foo::Trait>() -> S {
            unimplemented!()
        }
    """)

    fun `test substitute type alias`() = doAvailableTest("""
        type Foo = i32;

        fn foo(_: Foo/*caret*/) {}
    """, """
        type Foo = i32;

        fn foo(_: i32) {}
    """)

    fun `test substitute type alias to generic type`() = doAvailableTest("""
        struct S<T>(T);

        type Foo = S<i32>;

        fn foo(_: Foo/*caret*/) {}
    """, """
        struct S<T>(T);

        type Foo = S<i32>;

        fn foo(_: S<i32>) {}
    """)

    fun `test substitute generic type alias`() = doAvailableTest("""
        struct S<T>(T);

        type Foo<T> = S<T>;

        fn foo(_: Foo<u32>/*caret*/) {}
    """, """
        struct S<T>(T);

        type Foo<T> = S<T>;

        fn foo(_: S<u32>) {}
    """)

    fun `test substitute generic type alias with more generic parameters`() = doAvailableTest("""
        struct S<T, R>(T, R);

        type Foo<T> = S<T, bool>;

        fn foo(_: Foo<u32>/*caret*/) {}
    """, """
        struct S<T, R>(T, R);

        type Foo<T> = S<T, bool>;

        fn foo(_: S<u32, bool>) {}
    """)

    fun `test substitute type alias in expression context`() = doAvailableTest("""
        struct S;

        impl S {
            fn new() -> S { S }
        }

        type Foo = S;

        fn foo() {
            let _ = Foo/*caret*/::new();
        }
    """, """
        struct S;

        impl S {
            fn new() -> S { S }
        }

        type Foo = S;

        fn foo() {
            let _ = S::new();
        }
    """)

    fun `test substitute type alias to generic type in expression context`() = doAvailableTest("""
        struct Bar<T>(T);

        impl <T> Bar<T> {
            fn new() -> Bar<T> { unimplemented!() }
        }

        type Foo = Bar<u32>;

        fn foo() {
            let _ = Foo/*caret*/::new();
        }
    """, """
        struct Bar<T>(T);

        impl <T> Bar<T> {
            fn new() -> Bar<T> { unimplemented!() }
        }

        type Foo = Bar<u32>;

        fn foo() {
            let _ = Bar::<u32>/*caret*/::new();
        }
    """)

    fun `test substitute generic type alias in expression context with inference`() = doAvailableTest("""
        struct S<A>(A);

        impl <B> S<B> {
            fn new(t: B) -> S<B> { S(t) }
        }

        type Foo<C> = S<C>;

        fn foo() {
            let _ = Foo/*caret*/::new(1);
        }
    """, """
        struct S<A>(A);

        impl <B> S<B> {
            fn new(t: B) -> S<B> { S(t) }
        }

        type Foo<C> = S<C>;

        fn foo() {
            let _ = S::<i32>::new(1);
        }
    """)

    fun `test substitute generic type alias in expression context with inference 2`() = doAvailableTest(
        """
        struct S<A, B>(A, B);

        impl <A1, B1: Default> S<A1, B1> {
            fn new(t: A1) -> S<A1, B1> { S(t, B1::default()) }
        }

        type Foo<A2, B2> = S<A2, B2>;

        fn foo() {
            let _ = Foo::<_, u8>/*caret*/::new(1);
        }
    """, """
        struct S<A, B>(A, B);

        impl <A1, B1: Default> S<A1, B1> {
            fn new(t: A1) -> S<A1, B1> { S(t, B1::default()) }
        }

        type Foo<A2, B2> = S<A2, B2>;

        fn foo() {
            let _ = S::<i32, u8>::new(1);
        }
    """)

    fun `test substitute generic type alias in explicit expression context`() = doAvailableTest("""
        struct S<T>(T);

        impl <T> S<T> {
            fn new(t: T) -> S<T> { S(t) }
        }

        type Foo<T> = S<T>;

        fn foo() {
            let _ = Foo::<u32>/*caret*/::new(1);
        }
    """, """
        struct S<T>(T);

        impl <T> S<T> {
            fn new(t: T) -> S<T> { S(t) }
        }

        type Foo<T> = S<T>;

        fn foo() {
            let _ = S::<u32>::new(1);
        }
    """)

    fun `test substitute generic type alias in explicit expression context with default type parameter`() = doAvailableTest("""
        struct S<T, R, Z=u32>(T, R, Z);

        impl <T, R, Z> S<T, R, Z> {
            fn new(t: T, r: R, z: Z) -> S<T, R, Z> { S(t, r, z) }
        }

        type Foo<T> = S<T, bool>;

        fn foo() {
            let _ = Foo::<u32>/*caret*/::new(1, true, 3);
        }
    """, """
        struct S<T, R, Z=u32>(T, R, Z);

        impl <T, R, Z> S<T, R, Z> {
            fn new(t: T, r: R, z: Z) -> S<T, R, Z> { S(t, r, z) }
        }

        type Foo<T> = S<T, bool>;

        fn foo() {
            let _ = S::<u32, bool>::new(1, true, 3);
        }
    """)
}
