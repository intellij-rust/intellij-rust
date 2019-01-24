/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes


import org.rust.ide.inspections.RsAssignToImmutableInspection
import org.rust.ide.inspections.RsBorrowCheckerInspection
import org.rust.ide.inspections.RsMultipleInspectionsTestBase
import org.rust.ide.inspections.RsReassignImmutableInspection

class AddMutableFixTest : RsMultipleInspectionsTestBase(
    RsBorrowCheckerInspection(),
    RsAssignToImmutableInspection(),
    RsReassignImmutableInspection()
) {
    fun `test fix E0596 method call`() = checkFixByText("Make `self` mutable", """
        struct A {}
        impl A {
            fn foo(&mut self) {}
            fn bar (&self) {
                <error>self/*caret*/</error>.foo();
            }
        }
    """, """
        struct A {}
        impl A {
            fn foo(&mut self) {}
            fn bar (&mut self) {
                self.foo();
            }
        }
    """)

    fun `test fix E0596 field method call`() = checkFixByText("Make `self` mutable", """
        struct A {}
        impl A {
            fn foo(&mut self) {}
        }
        struct V { a: A }
        impl V {
            fn foo(&self) {
                <error>self.a/*caret*/</error>.foo()
            }
        }
    """, """
        struct A {}
        impl A {
            fn foo(&mut self) {}
        }
        struct V { a: A }
        impl V {
            fn foo(&mut self) {
                self.a.foo()
            }
        }
    """)

    fun `test fix E0596 let at method call`() = checkFixByText("Make `test` mutable", """
        struct S;
        impl S {
            fn test(&mut self) {}
        }
        fn main() {
            let test = S;
            <error>te/*caret*/st</error>.test();
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

    fun `test fix E0596 let at arg method call`() = checkFixByText("Make `test` mutable", """
        struct S;
        impl S {
            fn test(&self, test: &mut S) {}
        }
        fn main() {
            let s = S;
            let test = S;
            s.test(&mut <error>test/*caret*/</error>);
        }
    """, """
        struct S;
        impl S {
            fn test(&self, test: &mut S) {}
        }
        fn main() {
            let s = S;
            let mut test = S;
            s.test(&mut test);
        }
    """)

    fun `test fix E0596 let at func call`() = checkFixByText("Make `test` mutable", """
        struct S;
        fn foo(test: &mut S) {}
        fn main() {
            let test = S;
            foo(&mut <error>test/*caret*/</error>);
        }
    """, """
        struct S;
        fn foo(test: &mut S) {}
        fn main() {
            let mut test = S;
            foo(&mut test);
        }
    """)

    fun `test E0384 fix let at reassign`() = checkFixByText("Make `test` mutable", """
        fn main() {
            let test = 10;
            <error>test/*caret*/ = 5</error>;
        }
    """, """
        fn main() {
            let mut test = 10;
            test = 5;
        }
    """)

    fun `test fix E0384 let pattern at reassign`() = checkFixByText("Make `test` mutable", """
        fn main() {
            let (test, test2) = (10, 20);
            <error>test/*caret*/ = 5</error>;
        }
    """, """
        fn main() {
            let (mut test, test2) = (10, 20);
            test = 5;
        }
    """)

    fun `test fix E0384 let pattern at reassign 2`() = checkFixByText("Make `test2` mutable", """
        fn main() {
            let (test, test2) = (10, 20);
            <error>test2/*caret*/ = 5</error>;
        }
    """, """
        fn main() {
            let (test, mut test2) = (10, 20);
            test2 = 5;
        }
    """)

    fun `test fix E0384 function parameter`() = checkFixByText("Make `test` mutable", """
        fn test(test: i32) {
            <error>test/*caret*/ = 32</error>;
        }
    """, """
        fn test(mut test: i32) {
            test = 32;
        }
    """)

    /** Issue [3417](https://github.com/intellij-rust/intellij-rust/issues/3417) */
    fun `test fix E0384 tuple function parameter`() = checkFixByText("Make `test` mutable", """
        fn test((test, test2): (i32, i32)) {
            <error>test/*caret*/ = 32</error>;
        }
    """, """
        fn test((mut test, test2): (i32, i32)) {
            test = 32;
        }
    """)

    fun `test fix E0594 assign to field`() = checkFixByText("Make `foo` mutable", """
        struct Foo { a: i32 }
        fn main() {
            let foo = Foo { a: 1 };
            <error>foo.a/*caret*/ = 2</error>;
        }
    """, """
        struct Foo { a: i32 }
        fn main() {
            let mut foo = Foo { a: 1 };
            foo.a = 2;
        }
    """)

    fun `test fix E0594 assign to borrowed field`() = checkFixIsUnavailable("Make `bar` mutable", """
        struct Foo { a: i32 }
        fn main() {
            let mut foo = Foo { a: 1 };
            let bar = &foo;
            <error>bar.a/*caret*/ = 2</error>;
        }
    """)

    fun `test fix E0594 assign to array element`() = checkFixByText("Make `arr` mutable", """
        fn main() {
            let arr = [0; 3];
            <error>arr[0]/*caret*/ = 1</error>;
        }
    """, """
        fn main() {
            let mut arr = [0; 3];
            arr[0] = 1;
        }
    """)

    fun `test fix E0594 assign to borrowed array element`() = checkFixIsUnavailable("Make `arr2` mutable", """
        fn main() {
            let arr = [0; 3];
            let arr2 = &arr;
            <error>arr2[0]/*caret*/ = 1</error>;
        }
    """)

    fun `test fix E0594 assign to immutable reference`() = checkFixByText("Make `s` mutable", """
        struct S { a: i32 }
        fn foo(s: &S) {
            <error>s.a/*caret*/ = 42</error>;
        }
    """, """
        struct S { a: i32 }
        fn foo(s: &mut S) {
            s.a = 42;
        }
    """)

    fun `test fix E0594 assign to immutable reference with ref keyword`() = checkFixIsUnavailable("Make `s` mutable", """
        struct S { a: i32 }

        fn foo(ref s: &S) {
            <error>s.a/*caret*/ = 42</error>;
        }
    """)

    fun `test add mutable fix to parameter with lifetime`() = checkFixByText("Make `obj` mutable", """
        struct A {}

        impl A {
            fn foo(&mut self) {  }

            fn bar<'a>(&self, obj: &'a A) {
                <error>obj/*caret*/</error>.foo()
            }
        }
    """, """
        struct A {}

        impl A {
            fn foo(&mut self) {  }

            fn bar<'a>(&self, obj: &'a mut A) {
                obj.foo()
            }
        }
    """)
}
