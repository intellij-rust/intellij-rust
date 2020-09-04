/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class ConvertUFCSToMethodCallTest : RsIntentionTestBase(ConvertUFCSToMethodCallIntention::class) {
    fun `test not available for methods`() = doUnavailableTest("""
        struct S;
        impl S {
            fn foo(&self) {}
        }
        fn foo(s: S) {
            s./*caret*/foo();
        }
    """)

    fun `test not available for functions`() = doUnavailableTest("""
        fn foo() {}
        fn bar() {
            /*caret*/foo();
        }
    """)

    fun `test not available for associated functions`() = doUnavailableTest("""
        struct S;
        impl S {
            fn foo() {}
        }
        fn foo() {
            S::/*caret*/foo();
        }
    """)

    fun `test not available without self argument`() = doUnavailableTest("""
        struct S;
        impl S {
            fn foo(&self) {}
        }
        fn foo() {
            S::/*caret*/foo();
        }
    """)

    fun `test keep other arguments`() = doAvailableTest("""
        struct S;
        impl S {
            fn foo(&self, _: u32, _: u32) {}
        }
        fn foo(s: &S) {
            S::/*caret*/foo(&s, 1, 2);
        }
    """, """
        struct S;
        impl S {
            fn foo(&self, _: u32, _: u32) {}
        }
        fn foo(s: &S) {
            s.foo(1, 2);
        }
    """)

    fun `test self ref receiver move`() = doAvailableTest("""
        struct S;
        impl S {
            fn foo(&self) {}
        }
        fn foo(s: S) {
            S::/*caret*/foo(&s);
        }
    """, """
        struct S;
        impl S {
            fn foo(&self) {}
        }
        fn foo(s: S) {
            s.foo();
        }
    """)

    fun `test self ref receiver ref`() = doAvailableTest("""
        struct S;
        impl S {
            fn foo(&self) {}
        }
        fn foo(s: &S) {
            S::/*caret*/foo(s);
        }
    """, """
        struct S;
        impl S {
            fn foo(&self) {}
        }
        fn foo(s: &S) {
            s.foo();
        }
    """)

    fun `test self mut ref receiver move`() = doAvailableTest("""
        struct S;
        impl S {
            fn foo(&mut self) {}
        }
        fn foo(mut s: S) {
            S::/*caret*/foo(&mut s);
        }
    """, """
        struct S;
        impl S {
            fn foo(&mut self) {}
        }
        fn foo(mut s: S) {
            s.foo();
        }
    """)

    fun `test self mut ref receiver mut ref`() = doAvailableTest("""
        struct S;
        impl S {
            fn foo(&mut self) {}
        }
        fn foo(s: &mut S) {
            S::/*caret*/foo(s);
        }
    """, """
        struct S;
        impl S {
            fn foo(&mut self) {}
        }
        fn foo(s: &mut S) {
            s.foo();
        }
    """)

    fun `test function call self argument`() = doAvailableTest("""
        struct S;
        impl S {
            fn foo(&self) {}
        }
        fn bar() -> S { S }
        fn foo() {
            S::/*caret*/foo(bar());
        }
    """, """
        struct S;
        impl S {
            fn foo(&self) {}
        }
        fn bar() -> S { S }
        fn foo() {
            bar().foo();
        }
    """)

    fun `test complex self argument`() = doAvailableTest("""
        struct S;
        impl S {
            fn foo(&self) {}
        }
        fn foo() {
            S::/*caret*/foo(x1() + x2());
        }
    """, """
        struct S;
        impl S {
            fn foo(&self) {}
        }
        fn foo() {
            (x1() + x2()).foo();
        }
    """)

    fun `test generic struct`() = doAvailableTest("""
        struct S<T>(T);
        impl<T> S<T> {
            fn foo(&self, _: &T) {}
        }
        fn foo<T>(s: S<T>, t: T) {
            S::<T>::/*caret*/foo(&s, &t);
        }
    """, """
        struct S<T>(T);
        impl<T> S<T> {
            fn foo(&self, _: &T) {}
        }
        fn foo<T>(s: S<T>, t: T) {
            s.foo(&t);
        }
    """)

    fun `test trait`() = doAvailableTest("""
        trait Trait {
            fn foo(&self);
        }
        fn foo(s: &dyn Trait) {
            <dyn Trait>::/*caret*/foo(s);
        }
    """, """
        trait Trait {
            fn foo(&self);
        }
        fn foo(s: &dyn Trait) {
            s.foo();
        }
    """)
}
