/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class ConvertMethodCallToUFCSTest : RsIntentionTestBase(ConvertMethodCallToUFCSIntention()) {
    fun `test unavailable on function call`() = doUnavailableTest("""
        fn foo() {}
        fn bar() {
            /*caret*/foo();
        }
    """)

    fun `test unavailable on unresolved method`() = doUnavailableTest("""
        struct S;

        fn bar() {
            let s = S;
            s./*caret*/foo();
        }
    """)

    fun `test unavailable on method args`() = doUnavailableTest("""
        struct S;
        impl S {
            fn foo(&self, _: u32) {}
        }

        fn bar() {
            let s = S;
            s.foo(/*caret*/1);
        }
    """)

    fun `test self move`() = doAvailableTest("""
        struct Foo;
        impl Foo {
            fn foo(self) {}
        }
        fn foo(foo: Foo) {
            foo./*caret*/foo();
        }
    """, """
        struct Foo;
        impl Foo {
            fn foo(self) {}
        }
        fn foo(foo: Foo) {
            Foo::foo(foo);
        }
    """)

    fun `test self ref receiver owned`() = doAvailableTest("""
        struct Foo;
        impl Foo {
            fn foo(&self) {}
        }
        fn foo(foo: Foo) {
            foo./*caret*/foo();
        }
    """, """
        struct Foo;
        impl Foo {
            fn foo(&self) {}
        }
        fn foo(foo: Foo) {
            Foo::foo(&foo);
        }
    """)

    fun `test self ref receiver ref`() = doAvailableTest("""
        struct Foo;
        impl Foo {
            fn foo(&self) {}
        }
        fn foo(foo: &Foo) {
            foo./*caret*/foo();
        }
    """, """
        struct Foo;
        impl Foo {
            fn foo(&self) {}
        }
        fn foo(foo: &Foo) {
            Foo::foo(foo);
        }
    """)

    fun `test self mut ref receiver owned`() = doAvailableTest("""
        struct Foo;
        impl Foo {
            fn foo(&mut self) {}
        }
        fn foo(mut foo: Foo) {
            foo./*caret*/foo();
        }
    """, """
        struct Foo;
        impl Foo {
            fn foo(&mut self) {}
        }
        fn foo(mut foo: Foo) {
            Foo::foo(&mut foo);
        }
    """)

    fun `test self mut ref receiver mut ref`() = doAvailableTest("""
        struct Foo;
        impl Foo {
            fn foo(&mut self) {}
        }
        fn foo(foo: &mut Foo) {
            foo./*caret*/foo();
        }
    """, """
        struct Foo;
        impl Foo {
            fn foo(&mut self) {}
        }
        fn foo(foo: &mut Foo) {
            Foo::foo(foo);
        }
    """)

    fun `test nested attribute method call`() = doAvailableTest("""
        struct Bar;
        struct Foo {
            bar: Bar
        }
        impl Bar {
            fn bar(&self) {}
        }
        fn foo(foo: &Foo) {
            foo.bar./*caret*/bar();
        }
    """, """
        struct Bar;
        struct Foo {
            bar: Bar
        }
        impl Bar {
            fn bar(&self) {}
        }
        fn foo(foo: &Foo) {
            Bar::bar(&foo.bar);
        }
    """)

    fun `test trait method call on struct`() = doAvailableTest("""
        struct Foo;
        trait Trait {
            fn foo(&self);
        }
        impl Trait for Foo {
            fn foo(&self) {}
        }

        fn foo(foo: &Foo) {
            foo./*caret*/foo();
        }
    """, """
        struct Foo;
        trait Trait {
            fn foo(&self);
        }
        impl Trait for Foo {
            fn foo(&self) {}
        }

        fn foo(foo: &Foo) {
            Foo::foo(foo);
        }
    """)

    fun `test trait method call on generic trait bound`() = doAvailableTest("""
        trait Trait {
            fn foo(&self);
        }

        fn foo<T: Trait>(foo: &T) {
            foo./*caret*/foo();
        }
    """, """
        trait Trait {
            fn foo(&self);
        }

        fn foo<T: Trait>(foo: &T) {
            T::foo(foo);
        }
    """)

    fun `test trait method call on generic trait where`() = doAvailableTest("""
        trait Trait {
            fn foo(&self);
        }

        fn foo<T>(foo: T) where T: Trait {
            foo./*caret*/foo();
        }
    """, """
        trait Trait {
            fn foo(&self);
        }

        fn foo<T>(foo: T) where T: Trait {
            T::foo(&foo);
        }
    """)

    fun `test trait method call on impl trait`() = doAvailableTest("""
        trait Trait {
            fn foo(&self);
        }

        fn foo(foo: impl Trait) {
            foo./*caret*/foo();
        }
    """, """
        trait Trait {
            fn foo(&self);
        }

        fn foo(foo: impl Trait) {
            Trait::foo(&foo);
        }
    """)

    fun `test trait method call on impl trait ref`() = doAvailableTest("""
        trait Trait {
            fn foo(&self);
        }

        fn foo(foo: &impl Trait) {
            foo./*caret*/foo();
        }
    """, """
        trait Trait {
            fn foo(&self);
        }

        fn foo(foo: &impl Trait) {
            Trait::foo(foo);
        }
    """)

    fun `test trait method call on dyn trait`() = doAvailableTest("""
        trait Trait {
            fn foo(&self);
        }

        fn foo(foo: &dyn Trait) {
            foo./*caret*/foo();
        }
    """, """
        trait Trait {
            fn foo(&self);
        }

        fn foo(foo: &dyn Trait) {
            Trait::foo(foo);
        }
    """)

    fun `test target in a different mod `() = doAvailableTest("""
        mod foo {
            pub struct Foo;
            impl Foo {
                pub fn bar(&self) {}
            }
        }

        fn bar(a: foo::Foo) {
            a./*caret*/bar();
        }
    """, """
        use foo::Foo;

        mod foo {
            pub struct Foo;
            impl Foo {
                pub fn bar(&self) {}
            }
        }

        fn bar(a: foo::Foo) {
            Foo::bar(&a);
        }
    """)

    fun `test generic trait`() = doAvailableTest("""
        trait Trait<T> {
            fn foo(&self);
        }

        fn foo<T, R: Trait<T>>(foo: R) {
            foo./*caret*/foo();
        }
    """, """
        trait Trait<T> {
            fn foo(&self);
        }

        fn foo<T, R: Trait<T>>(foo: R) {
            R::foo(&foo);
        }
    """)

    fun `test generic trait bound impl`() = doAvailableTest("""
        trait Trait<T> {
            fn foo(&self);
        }

        fn foo<T, R: Trait<u32>>(foo: R) {
            foo./*caret*/foo();
        }
    """, """
        trait Trait<T> {
            fn foo(&self);
        }

        fn foo<T, R: Trait<u32>>(foo: R) {
            R::foo(&foo);
        }
    """)

    fun `test generic struct`() = doAvailableTest("""
        struct S<T>(T);
        impl<T> S<T> {
            fn foo(&self) {}
        }

        fn foo<T>(foo: S<T>) {
            foo./*caret*/foo();
        }
    """, """
        struct S<T>(T);
        impl<T> S<T> {
            fn foo(&self) {}
        }

        fn foo<T>(foo: S<T>) {
            S::foo(&foo);
        }
    """)

    fun `test trait with an associated type`() = doAvailableTest("""
        trait Trait {
            type Type;
            fn foo(&self) -> Self::Type;
        }

        fn foo(foo: &dyn Trait<Type=u32>) {
            foo./*caret*/foo();
        }
    """, """
        trait Trait {
            type Type;
            fn foo(&self) -> Self::Type;
        }

        fn foo(foo: &dyn Trait<Type=u32>) {
            Trait::foo(foo);
        }
    """)

    fun `test both inherent impl and trait impl`() = doAvailableTest("""
        trait Trait {
            fn foo(&self);
        }
        struct S;
        impl S {
            fn foo(&self) {}
        }
        impl Trait for S {
            fn foo(&self) {}
        }

        fn foo(foo: S) {
            foo./*caret*/foo();
        }
    """, """
        trait Trait {
            fn foo(&self);
        }
        struct S;
        impl S {
            fn foo(&self) {}
        }
        impl Trait for S {
            fn foo(&self) {}
        }

        fn foo(foo: S) {
            S::foo(&foo);
        }
    """)

    fun `test array type`() = doAvailableTest("""
        trait Trait {
            fn foo(&self);
        }
        impl Trait for [u8] {
            fn foo(&self) {}
        }

        fn foo(foo: &[u8]) {
            foo./*caret*/foo();
        }
    """, """
        trait Trait {
            fn foo(&self);
        }
        impl Trait for [u8] {
            fn foo(&self) {}
        }

        fn foo(foo: &[u8]) {
            <[u8]>::foo(foo);
        }
    """)
}
