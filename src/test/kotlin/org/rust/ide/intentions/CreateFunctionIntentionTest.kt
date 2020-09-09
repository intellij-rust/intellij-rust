/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class CreateFunctionIntentionTest : RsIntentionTestBase(CreateFunctionIntention::class) {
    fun `test unavailable on resolved function`() = doUnavailableTest("""
        fn foo() {}

        fn main() {
            /*caret*/foo();
        }
    """)

    fun `test unavailable on arguments`() = doUnavailableTest("""
        fn main() {
            foo(1/*caret*/);
        }
    """)

    fun `test unavailable on path argument`() = doUnavailableTest("""
        fn foo(a: u32) {}

        fn main() {
            foo(bar::baz/*caret*/);
        }
    """)

    fun `test create function`() = doAvailableTest("""
        fn main() {
            /*caret*/foo();
        }
    """, """
        fn main() {
            foo();
        }

        fn foo() {
            unimplemented!()/*caret*/
        }
    """)

    fun `test create function in an existing module`() = doAvailableTest("""
        mod foo {}

        fn main() {
            foo::bar/*caret*/();
        }
    """, """
        mod foo {
            pub(crate) fn bar() {
                unimplemented!()/*caret*/
            }
        }

        fn main() {
            foo::bar();
        }
    """)

    fun `test create function in an existing file`() = doAvailableTestWithFileTreeComplete("""
        //- main.rs
            mod foo;

            fn main() {
                foo::bar/*caret*/();
            }
        //- foo.rs
            fn test() {}
    """, """
        //- main.rs
            mod foo;

            fn main() {
                foo::bar();
            }
        //- foo.rs
            fn test() {}

            pub(crate) fn bar() {
                unimplemented!()
            }
    """)

    fun `test unresolved function call in a missing module`() = doUnavailableTest("""
        fn main() {
            foo::bar/*caret*/();
        }
    """)

    fun `test unresolved function call in a nested function`() = doAvailableTest("""
        fn main() {
            fn foo() {
                /*caret*/bar();
            }
        }
    """, """
        fn main() {
            fn foo() {
                bar();
            }
            fn bar() {
                unimplemented!()/*caret*/
            }
        }
    """)

    fun `test unresolved function call inside a module`() = doAvailableTest("""
        mod foo {
            fn main() {
                /*caret*/bar();
            }
        }
    """, """
        mod foo {
            fn main() {
                bar();
            }

            fn bar() {
                unimplemented!()/*caret*/
            }
        }
    """)

    fun `test simple parameters`() = doAvailableTest("""
        fn main() {
            let a = 5;
            foo/*caret*/(1, "hello", &a);
        }
    """, """
        fn main() {
            let a = 5;
            foo(1, "hello", &a);
        }

        fn foo(p0: i32, p1: &str, p2: &i32) {
            unimplemented!()
        }
    """)

    fun `test generic parameters`() = doAvailableTest("""
        trait Trait1 {}
        trait Trait2 {}

        fn foo<T, X, R: Trait1>(t1: T, t2: T, r: R) where T: Trait2 {
            bar/*caret*/(r, t1, t2);
        }
    """, """
        trait Trait1 {}
        trait Trait2 {}

        fn foo<T, X, R: Trait1>(t1: T, t2: T, r: R) where T: Trait2 {
            bar(r, t1, t2);
        }

        fn bar<T, R: Trait1>(p0: R, p1: T, p2: T) where T: Trait2 {
            unimplemented!()
        }
    """)

    fun `test complex generic constraints inside impl`() = doAvailableTest("""
        struct S<T>(T);
        trait Trait {}
        trait Trait2 {}

        impl<'a, 'b, T: 'a> S<T> where for<'c> T: Trait + Fn(&'c i32) {
            fn foo<R>(t: T, r: &R) where T: Trait2 + Trait, R: Trait + for<'d> Fn(&'d i32) {
                bar/*caret*/(t, r);
            }
        }
    """, """
        struct S<T>(T);
        trait Trait {}
        trait Trait2 {}

        impl<'a, 'b, T: 'a> S<T> where for<'c> T: Trait + Fn(&'c i32) {
            fn foo<R>(t: T, r: &R) where T: Trait2 + Trait, R: Trait + for<'d> Fn(&'d i32) {
                bar(t, r);
            }
        }

        fn bar<'a, R, T: 'a>(p0: T, p1: &R) where R: Trait + for<'d> Fn(&'d i32), T: Trait + Trait2, for<'c> T: Fn(&'c i32) + Trait {
            unimplemented!()
        }
    """)

    fun `test nested function generic parameters`() = doAvailableTest("""
        fn foo<T>() where T: Foo {
            fn bar<T>(t: T) where T: Bar {
                baz/*caret*/(t);
            }
        }
    """, """
        fn foo<T>() where T: Foo {
            fn bar<T>(t: T) where T: Bar {
                baz(t);
            }
            fn baz<T>(p0: T) where T: Bar {
                unimplemented!()
            }
        }
    """)

    fun `test guess return type let decl`() = doAvailableTest("""
        fn foo() {
            let x: u32 = bar/*caret*/();
        }
    """, """
        fn foo() {
            let x: u32 = bar();
        }

        fn bar() -> u32 {
            unimplemented!()
        }
    """)

    fun `test guess return type assignment`() = doAvailableTest("""
        fn foo() {
            let mut x: u32 = 0;
            x = bar/*caret*/();
        }
    """, """
        fn foo() {
            let mut x: u32 = 0;
            x = bar();
        }

        fn bar() -> u32 {
            unimplemented!()
        }
    """)

    fun `test guess return type function call`() = doAvailableTest("""
        fn bar(x: u32) {}
        fn foo() {
            bar(baz/*caret*/());
        }
    """, """
        fn bar(x: u32) {}
        fn foo() {
            bar(baz());
        }

        fn baz() -> u32 {
            unimplemented!()
        }
    """)

    fun `test guess return type method call`() = doAvailableTest("""
        struct S;
        impl S {
            fn bar(&self, x: u32) {}
        }
        fn foo(s: S) {
            s.bar(baz/*caret*/());
        }
    """, """
        struct S;
        impl S {
            fn bar(&self, x: u32) {}
        }
        fn foo(s: S) {
            s.bar(baz());
        }

        fn baz() -> u32 {
            unimplemented!()
        }
    """)

    fun `test guess return type struct literal`() = doAvailableTest("""
        struct S {
            a: u32
        }
        fn foo() {
            S {
                a: baz/*caret*/()
            };
        }
    """, """
        struct S {
            a: u32
        }
        fn foo() {
            S {
                a: baz()
            };
        }

        fn baz() -> u32 {
            unimplemented!()
        }
    """)

    fun `test guess return type self parameter`() = doAvailableTest("""
        struct S;
        impl S {
            fn bar(&self) {}
        }
        fn foo() {
            S::bar(baz/*caret*/());
        }
    """, """
        struct S;
        impl S {
            fn bar(&self) {}
        }
        fn foo() {
            S::bar(baz());
        }

        fn baz() -> &S {
            unimplemented!()
        }
    """)

    fun `test guess return type generic parameter`() = doAvailableTest("""
        fn foo<T>() {
            let x: T = bar/*caret*/();
        }
    """, """
        fn foo<T>() {
            let x: T = bar();
        }

        fn bar<T>() -> T {
            unimplemented!()
        }
    """)

    fun `test navigate to created function`() = doAvailableTest("""
        fn foo() {
            bar/*caret*/();
        }
    """, """
        fn foo() {
            bar();
        }

        fn bar() {
            unimplemented!()/*caret*/
        }
    """)

    fun `test create method create impl`() = doAvailableTest("""
        trait Trait {}
        struct S<T>(T) where T: Trait;

        fn foo(s: S<u32>) {
            s.foo/*caret*/(1, 2);
        }
    """, """
        trait Trait {}
        struct S<T>(T) where T: Trait;

        impl<T> S<T> where T: Trait {
            pub(crate) fn foo(&self, p0: i32, p1: i32) {
                unimplemented!()
            }
        }

        fn foo(s: S<u32>) {
            s.foo(1, 2);
        }
    """)

    fun `test create method no arguments`() = doAvailableTest("""
        struct S;

        fn foo(s: S) {
            s.foo/*caret*/();
        }
    """, """
        struct S;

        impl S {
            pub(crate) fn foo(&self) {
                unimplemented!()
            }
        }

        fn foo(s: S) {
            s.foo();
        }
    """)

    fun `test create generic method`() = doAvailableTest("""
        struct S;

        fn foo<R>(s: S, r: R) {
            s.foo/*caret*/(r);
        }
    """, """
        struct S;

        impl S {
            pub(crate) fn foo<R>(&self, p0: R) {
                unimplemented!()
            }
        }

        fn foo<R>(s: S, r: R) {
            s.foo(r);
        }
    """)

    fun `test create method inside impl`() = doAvailableTest("""
        struct S;
        impl S {
            fn foo(&self) {
                self.bar/*caret*/(0);
            }
        }
    """, """
        struct S;
        impl S {
            fn foo(&self) {
                self.bar(0);
            }
            fn bar(&self, p0: i32) {
                unimplemented!()
            }
        }
    """)

    fun `test create method inside generic impl`() = doAvailableTest("""
        struct S<T>(T);
        impl<T> S<T> {
            fn foo(&self, t: T) {
                self.bar/*caret*/(t);
            }
        }
    """, """
        struct S<T>(T);
        impl<T> S<T> {
            fn foo(&self, t: T) {
                self.bar(t);
            }
            fn bar(&self, p0: T) {
                unimplemented!()
            }
        }
    """)

    fun `test create method inside generic impl with where`() = doAvailableTest("""
        trait Trait {}
        struct S<T>(T);
        impl<T> S<T> where T: Trait {
            fn foo(&self, t: T) {
                self.bar/*caret*/(t);
            }
        }
    """, """
        trait Trait {}
        struct S<T>(T);
        impl<T> S<T> where T: Trait {
            fn foo(&self, t: T) {
                self.bar(t);
            }
            fn bar(&self, p0: T) {
                unimplemented!()
            }
        }
    """)

    fun `test unavailable inside method arguments`() = doUnavailableTest("""
        struct S;
        fn foo(s: S) {
            s.bar(1, /*caret*/2);
        }
    """)

    fun `test available inside method name`() = doAvailableTest("""
        struct S;
        fn foo(s: S) {
            s.b/*caret*/ar(1, 2);
        }
    """, """
        struct S;

        impl S {
            pub(crate) fn bar(&self, p0: i32, p1: i32) {
                unimplemented!()
            }
        }

        fn foo(s: S) {
            s.bar(1, 2);
        }
    """)

    fun `test available after method name`() = doAvailableTest("""
        struct S;
        fn foo(s: S) {
            s.bar/*caret*/(1, 2);
        }
    """, """
        struct S;

        impl S {
            pub(crate) fn bar(&self, p0: i32, p1: i32) {
                unimplemented!()
            }
        }

        fn foo(s: S) {
            s.bar(1, 2);
        }
    """)

    fun `test guess method return type`() = doAvailableTest("""
        struct S;
        fn foo(s: S) {
            let a: u32 = s.bar/*caret*/(1, 2);
        }
    """, """
        struct S;

        impl S {
            pub(crate) fn bar(&self, p0: i32, p1: i32) -> u32 {
                unimplemented!()
            }
        }

        fn foo(s: S) {
            let a: u32 = s.bar(1, 2);
        }
    """)

    fun `test create method inside trait impl`() = doAvailableTest("""
        trait Trait {
            fn foo(&self);
        }
        struct S;
        impl Trait for S {
            fn foo(&self) {
                self.bar/*caret*/();
            }
        }
    """, """
        trait Trait {
            fn foo(&self);
        }
        struct S;

        impl S {
            pub(crate) fn bar(&self) {
                unimplemented!()
            }
        }

        impl Trait for S {
            fn foo(&self) {
                self.bar();
            }
        }
    """)

    fun `test create method inside different impl`() = doAvailableTest("""
        struct S;
        struct T;
        impl T {
            fn foo(&self, s: S) {
                s.bar/*caret*/();
            }
        }
    """, """
        struct S;

        impl S {
            pub(crate) fn bar(&self) {
                unimplemented!()
            }
        }

        struct T;
        impl T {
            fn foo(&self, s: S) {
                s.bar();
            }
        }
    """)
}
