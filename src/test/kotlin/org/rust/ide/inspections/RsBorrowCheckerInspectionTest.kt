/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsBorrowCheckerInspectionTest : RsInspectionsTestBase(RsBorrowCheckerInspection(), useStdLib = true) {

    fun `test mutable used at ref mutable method call (self)`() = checkByText("""
        struct S;
        impl S {
            fn test(&mut self) {}
        }
        fn main() {
            let mut test = S;
            test.test();
        }
    """)

    fun `test mutable used at mutable method call (self)`() = checkByText("""
        struct S;
        impl S {
            fn test(mut self) {}
        }
        fn main() {
            let test = S;
            test.test();
        }
    """)

    fun `test mutable used at mutable method call (args)`() = checkByText("""
        struct S;
        impl S {
            fn test(&self, test: &mut S) {}
        }
        fn main() {
            let test = S;
            let mut reassign = S;
            test.test(&mut reassign);
        }
    """)

    fun `test mutable used at mutable call`() = checkByText("""
        struct S;
        fn test(test: &mut S) {}
        fn main() {
            let mut s = S;
            test(&mut s);
        }
    """)

    fun `test immutable used at mutable call (pattern)`() = checkByText("""
        struct S;
        impl S {
            fn foo(&mut self) {}
        }
        fn test((x,y): (&S, &mut S)) {
            <error>x</error>.foo();
        }
    """)

    fun `test mutable used at mutable call (pattern)`() = checkByText("""
        struct S;
        impl S {
            fn foo(&mut self) {}
        }
        fn test((x,y): (&mut S, &mut S)) {
            x.foo();
        }
    """)

    fun `test mutable used at mutable method definition`() = checkByText("""
        struct S;
        impl S {
            fn test(&mut self) {
                self.foo();
            }
            fn foo(&mut self) {}
        }
    """)

    fun `test mutable type should not annotate`() = checkByText("""
        struct S;
        impl S {
            fn foo(&mut self) {}
        }
        trait Test {
            fn test(self);
        }
        impl<'a> Test for &'a mut S {
            fn test(self) {
                self.foo();
            }
        }
    """)

    fun `test immutable used at mutable method definition`() = checkByText("""
        struct S;
        impl S {
            fn test(&self) {
                <error descr="Cannot borrow immutable local variable `self` as mutable">self</error>.foo();
            }
            fn foo(&mut self) {}
        }
    """)

    fun `test immutable used at reference mutable function definition`() = checkByText("""
        struct S;
        impl S {
            fn foo(&mut self) {}
        }
        fn test(test: &S) {
            <error>test</error>.foo();
        }
    """)

    fun `test mutable used at reference mutable function definition`() = checkByText("""
        struct S;
        impl S {
            fn foo(&mut self) {}
        }
        fn test(test: &mut S) {
            test.foo();
        }
    """)

    fun `test no highlight for mutable for loops`() = checkByText("""
        fn test() {
            let mut xs: Vec<Vec<usize>> = vec![vec![1, 2], vec![3, 4]];
            for test in &mut xs {
                test.push(0);
            }
        }
    """)

    fun `test immutable used at reassign`() = checkByText("""
        fn main() {
            let a;
            if true {
                a = 10;
            } else {
                a = 20;
            }
            a = 5;//FIXME(farodin91): this line should fail
        }
    """)

    fun `test mutable used at reassign`() = checkByText("""
        fn main() {
            let mut x;
            x = 3;
            x = 5;
        }
    """)

    fun `test let some from mutable reference`() = checkByText("""
        fn foo(optional: Option<&mut String>) {
            if let Some(x) = optional {
                *x = "str".to_string();
            }
        }
    """)

    fun `test simple enum variant is treated as mutable`() = checkByText("""
        enum Foo { FOO }
        fn foo (f: &mut Foo) {}
        fn bar () {
            foo(&mut Foo::FOO);     // Must not be highlighted
        }
    """)

    fun `test mutable reference to empty struct with and without braces`() = checkByText("""
        struct S;

        fn main() {
            let test1 = &mut S; // Must not be highlighted
            let test2 = &mut S {}; // Must not be highlighted
        }
    """)

    fun `test fix method at method call (self)`() = checkFixByText("Make `self` mutable", """
        struct S;
        impl S {
            fn test(&self) {
                <error>sel<caret>f</error>.foo();
            }
            fn foo(&mut self) {}
        }
    """, """
        struct S;
        impl S {
            fn test(&mut self) {
                self.foo();
            }
            fn foo(&mut self) {}
        }
    """)

    fun `test fix method at method call (args)`() = checkFixByText("Make `s` mutable", """
        struct S;
        impl S {
            fn test(&self, s: &S) {
                <error>s<caret></error>.foo();
            }
            fn foo(&mut self) {}
        }
    """, """
        struct S;
        impl S {
            fn test(&self, s: &mut S) {
                s.foo();
            }
            fn foo(&mut self) {}
        }
    """)

    fun `test immutable used at ref mutable method call (self)`() = checkByText("""
        struct S;
        impl S {
            fn test(&mut self) {}
        }
        fn main() {
            let test = S;
            <error>test</error>.test();
        }
    """)

    fun `test immutable used at mutable method call (args)`() = checkByText("""
        struct S;
        impl S {
            fn test(&self, test: &mut S) {}
        }
        fn main() {
            let test = S;
            let reassign = S;
            test.test(&mut <error descr="Cannot borrow immutable local variable `reassign` as mutable">reassign</error>);
        }
    """)

    fun `test immutable used at mutable call`() = checkByText("""
        struct S;
        fn test(test: &mut S) {}
        fn main() {
            let s = S;
            test(&mut <error>s</error>);
        }
    """)

    fun `test fix let at method call (self)`() = checkFixByText("Make `test` mutable", """
        struct S;
        impl S {
            fn test(&mut self) {}
        }
        fn main() {
            let test = S;
            <error>te<caret>st</error>.test();
        }
    """, """
        struct S;
        impl S {
            fn test(&mut self) {}
        }
        fn main() {
            let mut test = S;
            test.test();
        }
    """)

    fun `test fix let at method call (args)`() = checkFixByText("Make `reassign` mutable", """
        struct S;
        impl S {
            fn test(&self, test: &mut S) {}
        }
        fn main() {
            let test = S;
            let reassign = S;
            test.test(&mut <error>reassi<caret>gn</error>);
        }
    """, """
        struct S;
        impl S {
            fn test(&self, test: &mut S) {}
        }
        fn main() {
            let test = S;
            let mut reassign = S;
            test.test(&mut reassign);
        }
    """)

    fun `test fix let at call (args)`() = checkFixByText("Make `s` mutable", """
        struct S;
        fn test(test: &mut S) {}
        fn main() {
            let s = S;
            test(&mut <error>s<caret></error>);
        }
    """, """
        struct S;
        fn test(test: &mut S) {}
        fn main() {
            let mut s = S;
            test(&mut s);
        }
    """)

    fun `test &mut on function`() = checkByText("""
        fn foo() {}

        fn main() {
            let local = &mut foo;
        }
    """)

    fun `test &mut on method`() = checkByText("""
        struct A {}
        impl A {
            fn foo(&mut self) {}
        }

        fn main() {
            let local = &mut A::foo;
        }
    """)
}
