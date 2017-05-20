package org.rust.ide.annotator

class RsExpressionAnnotatorTest : RsAnnotatorTestBase() {
    override val dataPath = "org/rust/ide/annotator/fixtures/expressions"

    fun testUnnecessaryParens() = checkWarnings("""

        struct S { f: i32 }

        fn test() {
            if <weak_warning descr="Predicate expression has unnecessary parentheses">(true)</weak_warning> {
                let _ = 1;
            }

            for x in <weak_warning descr="For loop expression has unnecessary parentheses">(0..10)</weak_warning> {
                let _ = 1;
            }

            match <weak_warning descr="Match expression has unnecessary parentheses">(x)</weak_warning> {
                _ => println!("Hello world")
            }

            if <weak_warning descr="Predicate expression has unnecessary parentheses">(pred)</weak_warning> {
                return <weak_warning descr="Return expression has unnecessary parentheses">(true)</weak_warning>;
            }

            while <weak_warning descr="Predicate expression has unnecessary parentheses">(true)</weak_warning> {
                let _ = 1;
            }

            let e = (<weak_warning descr="Redundant parentheses in expression">(4 + 3)</weak_warning>);
            let _ = match <weak_warning descr="Match expression has unnecessary parentheses">(1)</weak_warning> { _ => 0 };
            let _ = match (S { f: 0 }) { _ => 0 };

            if (S { f: 0 } == S { f: 1 }) {
                let _ = 1;
            }
        }
        """)


    fun testStructExpr() = checkWarnings("""

        #[derive(Default)]
        struct S {
            foo: i32,
            bar: i32
        }

        struct Empty {}

        enum E {
            V {
                foo: i32
            }
        }

        fn main() {
            let _ = S {
                foo: 92,
                <error descr="No such field">baz</error>: 62,
                bar: 11,
            };

            let _ = S {
                foo: 92,
                ..S::default()
            };

            let _ = <error descr="Some fields are missing">S</error> {
                foo: 92,
            };

            let _ = <error descr="Some fields are missing">S</error> {
                foo: 1,
                <error descr="Duplicate field">foo</error>: 2,
            };

            let _ = S {
                foo: 1,
                <error descr="Duplicate field">foo</error>: 2,
                ..S::default()
            };

            let _ = Empty { };

            let _ = <error descr="Some fields are missing">E::V</error> {
                <error descr="No such field">bar</error>: 92
            };
        }

        struct Win {
            foo: i32,
            #[cfg(windows)] bar: i32,
        }

        #[cfg(unix)]
        fn unix_only() {
            let w = Win { foo: 92 };
        }
    """)

    fun testUnion() = checkWarnings("""
        union U { a: i32, b: f32 }

        fn main() {
            let _ = U { a: 92 };
            let _ = U <error descr="Union expressions should have exactly one field">{ a: 92, b: 92.0}</error>;
        }
    """)

    fun testE0384_ReassignImmutableFromBinding() = checkErrors("""
        fn main() {
            let x = 5;
            <error descr="Reassigning an immutable variable [E0384]">x</error> = 3;
        }
    """)

    fun testE0384_ReassignMutableFromBinding() = checkErrors("""
        fn main() {
            let mut x = 5;
            x = 3;
        }
    """)

    fun testE0384_ReassignMutableFromStatic() = checkErrors("""
        fn main() {
            static mut X: u32 = 5;
            X = 3;
        }
    """)

    fun testE0384_ReassignMutableFromBindingWithoutAssignement() = checkErrors("""
        fn main() {
            let mut x;
            x = 3;
        }
    """)

    fun testE0384_ReassignImmutableFromBindingWithoutAssignement() = checkErrors("""
        fn main() {
            let x;
            x = 3;
        }
    """)

    fun testE0384_ReassignMutableFromBindingThroughAsterisk() = checkErrors("""
        fn main() {
            let mut x = 3;
            {
                let y = &x;
                *y = 5;
            }
        }
    """)

    fun testE0384_ReassignImmutableFromBindingThroughAsterisk() = checkErrors("""
        fn main() {
            let x = 3;
            {
                let y = &x;
                *y = 5;
            }
        }
    """)

    fun `test E0384 in pattern`() = checkErrors("""
        fn main() {
            let (x, mut y) = (92, 62);
            <error descr="Reassigning an immutable variable [E0384]">x</error> = 42;
            y = 42;
        }
    """)

    fun `test need unsafe function`() = checkErrors("""
        struct S;

        impl S {
            unsafe fn foo(&self) { return; }
        }

        fn main() {
            let s = S;
            <error descr="Call to unsafe function requires unsafe function or block [E0133]">s.foo()</error>;
        }
    """)

    fun `test need unsafe block`() = checkErrors("""
        struct S;

        impl S {
            unsafe fn foo(&self) { return; }
        }

        fn main() {
            {
                let s = S;
                <error descr="Call to unsafe function requires unsafe function or block [E0133]">s.foo()</error>;
            }
        }
    """)

    fun `test need unsafe 2`() = checkErrors("""
        unsafe fn foo() { return; }

        fn main() {
            <error>foo()</error>;
        }
    """)

    fun `test is unsafe block`() = checkErrors("""
        unsafe fn foo() {}

        fn main() {
            unsafe {
                {
                    foo();
                }
            }
        }
    """)

    fun `test is unsafe function`() = checkErrors("""
        unsafe fn foo() {}

        fn main() {
            unsafe {
                fn bar() {
                    <error>foo()</error>;
                }
            }
        }
    """)

    fun `test unsafe call unsafe`() = checkErrors("""
        unsafe fn foo() {}
        unsafe fn bar() { foo(); }
    """)

    fun `test pointer dereference`() = checkErrors("""
        fn main() {
            let char_ptr: *const char = 42 as *const _;
            let val = <error descr="Dereference of raw pointer requires unsafe function or block [E0133]">*char_ptr</error>;
        }
    """)

    fun `test pointer dereference in unsafe block`() = checkErrors("""
        fn main() {
            let char_ptr: *const char = 42 as *const _;
            let val = unsafe { *char_ptr };
        }
    """)

    fun `test pointer dereference in unsafe fn`() = checkErrors("""
        fn main() {
        }
        unsafe fn foo() {
            let char_ptr: *const char = 42 as *const _;
            let val = *char_ptr;
        }
    """)

    fun `test immutable used at ref mutable method call (self)`() = checkErrors("""
        struct S;

        impl S {
            fn test(&mut self) {
                unimplemented!();
            }
        }

        fn main() {
            let test = S;
            <error>test</error>.test();
        }
    """)

    fun `test mutable used at ref mutable method call (self)`() = checkErrors("""
        struct S;

        impl S {
            fn test(&mut self) {
                unimplemented!();
            }
        }

        fn main() {
            let mut test = S;
            test.test();
        }
    """)

    fun `test mutable used at mutable method call (self)`() = checkErrors("""
        struct S;

        impl S {
            fn test(mut self) {
                unimplemented!();
            }
        }

        fn main() {
            let test = S;
            test.test();
        }
    """)

    fun `test mutable used at mutable method call (args)`() = checkErrors("""
        struct S;

        impl S {
            fn test(&self, test: &mut S) {
                unimplemented!();
            }
        }

        fn main() {
            let test = S;
            let mut reassign = S;
            test.test(&mut reassign);
        }
    """)

    fun `test immutable used at mutable method call (args)`() = checkErrors("""
        struct S;

        impl S {
            fn test(&self, test: &mut S) {
                unimplemented!();
            }
        }

        fn main() {
            let test = S;
            let reassign = S;
            test.test(&mut <error>reassign</error>);
        }
    """)

    fun `test immutable used at mutable call`() = checkErrors("""
        struct S;

        fn test(test: &mut S) {
            unimplemented!();
        }

        fn main() {
            let s = S;
            test(&mut <error>s</error>);
        }
    """)

    fun `test mutable used at mutable call`() = checkErrors("""
        struct S;

        fn test(test: &mut S) {
            unimplemented!();
        }

        fn main() {
            let mut s = S;
            test(&mut s);
        }
    """)

    fun `test immutable used at mutable call (pattern)`() = checkErrors("""
        struct S;

        impl S {
            fn foo(&mut self) {
                unimplemented!();
            }
        }

        fn test((x,y): (&S, &mut S)) {
            <error>x</error>.foo();
        }
    """)

    fun `test mutable used at mutable call (pattern)`() = checkErrors("""
        struct S;

        impl S {
            fn foo(&mut self) {
                unimplemented!();
            }
        }

        fn test((x,y): (&mut S, &mut S)) {
            x.foo();
        }
    """)

    fun `test mutable used at mutable method definition`() = checkErrors("""
        struct S;

        impl S {
            fn test(&mut self) {
                self.foo();
            }
            fn foo(&mut self) {
                unimplemented!();
            }
        }
    """)

    fun `test mutable type should not annotate`() = checkErrors("""
        struct S;

        impl S {
            fn foo(&mut self) {
                unimplemented!();
            }
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

    fun `test immutable used at mutable method definition`() = checkErrors("""
        struct S;

        impl S {
            fn test(&self) {
                <error>self</error>.foo();
            }
            fn foo(&mut self) {
                unimplemented!();
            }
        }
    """)

    fun `test mutable used at mutable function definition`() = checkErrors("""
        fn test(mut test: i32) {
            test = 10
        }
    """)

    fun `test immutable used at mutable function definition`() = checkErrors("""
        fn test(test: i32) {
            <error>test</error> = 10
        }
    """)

    fun `test immutable used at mutable function definition (pattern)`() = checkErrors("""
        fn foo((x, y): (i32, i32)) {
            <error>x</error> = 92;
        }
    """)

    fun `test mutable used at mutable function definition (pattern)`() = checkErrors("""
        fn foo((mut x, y): (i32, i32)) {
            x = 92;
        }
    """)

    fun `test immutable used at mutable function definition (pattern) 2`() = checkErrors("""
        fn foo((x, y): (i32, i32)) {
            <error>y</error> = 92;
        }
    """)

    fun `test mutable used at mutable function definition (pattern) 2`() = checkErrors("""
        fn foo((x, mut y): (i32, i32)) {
            y = 92;
        }
    """)

    fun `test immutable used at reference mutable function definition`() = checkErrors("""
        struct S;

        impl S {
            fn foo(&mut self) {
                unimplemented!();
            }
        }

        fn test(test: &S) {
            <error>test</error>.foo()
        }
    """)

    fun `test mutable used at reference mutable function definition`() = checkErrors("""
        struct S;

        impl S {
            fn foo(&mut self) {
                unimplemented!();
            }
        }

        fn test(test: &mut S) {
            test.foo()
        }
    """)

    fun `test immutable used at reassign`() = checkErrors("""
        fn main() {
            let a;
            if(true) {
                a = 10;
            } else {
                a = 20;
            }
            a = 5;//FIXME(farodin91): this line should fail
        }
    """)

    fun `test mutable used at reassign`() = checkErrors("""
        fn main() {
            let mut x;
            x = 3;
            x = 5;
        }
    """)

    fun `test let some from mutable reference`() = checkErrors("""
        fn foo(optional: Option<&mut Vec<String>>) {
            if let Some(x) = optional {
                *x = "str".to_string();
            }
        }
    """)

    fun `test simple enum variant is treated as mutable`() = checkErrors("""
        enum Foo { FOO }
        fn foo (f: &mut Foo) {}
        fn bar () {
            foo(&mut Foo::FOO);     // Must not be annotated
        }
    """)
}
