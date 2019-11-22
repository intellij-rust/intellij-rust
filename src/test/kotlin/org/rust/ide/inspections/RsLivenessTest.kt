/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsLivenessTest : RsInspectionsTestBase(RsLivenessInspection::class) {

    fun `test dead argument`() = checkFixByText(
        "Rename to `_x`", """
        fn foo(<warning descr="Parameter `x` is never used">x/*caret*/</warning>: i32) -> i32 {
            return 42;
        }
    """, """
        fn foo(_x: i32) -> i32 {
            return 42;
        }
    """
    )

    fun `test dead uninit variable`() = checkFixByText(
        "Rename to `_x`", """
        fn foo() {
            let <warning descr="Variable `x` is never used">x/*caret*/</warning>: i32;
            let y = 5;
            y;
        }
    """, """
        fn foo() {
            let _x: i32;
            let y = 5;
            y;
        }
    """
    )

    fun `test dead init variable`() = checkByText(
        """
        fn foo() {
            let <warning descr="Variable `x` is never used">x</warning> = 1;
            let y = 5;
            y;
        }
    """
    )

    fun `test live argument return expr`() = checkByText(
        """
        fn foo(x: i32) -> i32 {
            return x + 5;
        }
    """
    )

    fun `test live argument condition`() = checkByText(
        """
        fn foo(cond: bool) -> i32 {
            if cond { 1 } else { 2 }
        }
    """
    )

    fun `test live variable return expr`() = checkByText(
        """
        fn foo() -> i32 {
            let x = 10;
            return x + 5;
        }
    """
    )

    fun `test live variable expr stmt`() = checkByText(
        """
        fn foo() {
            let x = 10;
            x;
        }
    """
    )

    fun `test live variable for expr 1`() = checkByText(
        """
        fn foo() {
            let x = 10;
            for <warning descr="Variable `i` is never used">i</warning> in 0..5 {
                x;
            }
        }
    """
    )

    fun `test live variable for expr 2`() = checkByText(
        """
        fn foo() {
            let x = 10;
            for <warning descr="Variable `i` is never used">i</warning> in x {}
        }
    """
    )

    fun `test live variable call 1`() = checkByText(
        """
        fn foo() {
            let x = 1;
            bar(1, x).baz(2);
        }
    """
    )

    fun `test live variable call 2`() = checkByText(
        """
        fn foo() {
            let x = 1;
            baz(0, quuz::bar(1, x)).foobar();
        }
    """
    )

    fun `test live variable call 3`() = checkByText(
        """
        fn foo() {
            let x = 1;
            x.bar();
        }
    """
    )

    fun `test live variable call 4`() = checkByText(
        """
        fn foo() {
            let x = 1;
            x.bar();
        }
    """
    )

    fun `test live variable closure 1`() = checkByText(
        """
        fn foo() {
            xs.map(|x| x.bar());
        }
    """
    )

    fun `test live variable closure 2`() = checkByText(
        """
        fn foo() {
            bar(&xs.map(|s| s));
        }
    """
    )

    fun `test live variable &mut`() = checkByText(
        """
        fn foo() {
            let mut x = 1;
            bar(&mut x);
        }
    """
    )

    fun `test live variable match 1`() = checkByText(
        """
        fn foo() {
            let x = 1;
            match (x, 1) {
                _ => {}
            }
        }
    """
    )

    fun `test live variable match 2`() = checkByText(
        """
        fn foo() {
            let x = 1;
            match cond {
                true => x,
                _ => 0
            };
        }
    """
    )

    fun `test live variable match 3`() = checkByText(
        """
        enum E { A, B(i32) }
        
        fn bar(_x: i32) {}
        
        use E::*;
        fn foo(e: E) {
            let _ = match e {
                A => 1,
                B(x) => {
                    quux::bazz(x.bar());
                    2
                }
            };
            
            let _ = match Config::default() {
                Ok(cfg) => cfg,
                Err(e) => {
                    let mut shell = Shell::new();
                    cargo::exit_with_error(e.into(), &mut shell)
                }
            };
        }
    """
    )

    fun `test live variable macro calls`() = checkByText(
        """
        macro_rules! my_macro {
            ($ e:expr) => ($ e);
        }

        fn foo() {
            let x = 1;
            let y = 2;
            println!("{}", x);
            my_macro!(y);
        }
    """
    )

    fun `test live variable deref`() = checkByText(
        """
        struct S;
        fn foo() {
            let x = &&S;
            if cond {
                bar(**x);
            }
        }
    """
    )

    fun `test live variable extend expr stmt`() = checkByText(
        """
        struct S { a: i32, b: i32 }

        fn foo() {
            let x = S { a: 1, b: 2 };
            x.a;
        }
    """
    )

    fun `test live argument extend expr stmt`() = checkByText(
        """
        struct S { a: i32, b: i32 }

        fn foo(x: S) {
            x.a;
        }
    """
    )

    fun `test live variable extend call expr`() = checkByText(
        """
        struct S { t: T, a: i32 }
        struct T { b: i32 }

        fn foo() {
            let x = S { t: T { b: 1 }, a: 2 };
            if cond {
                bar(x.t.b);
            }
        }
    """
    )

    fun `test live parameter closure`() = checkByText(
        """
        fn foo(x: i32) {
            let _f = |y: i32| -> i32 { x + y };
        }
    """
    )

    fun `test live parameter short struct literal`() = checkByText(
        """
        struct S { x: i32 }
        fn foo(x: i32) {
            S { x };
        }
    """
    )

    fun `test unresolved macro call`() = checkByText(
        """
        fn main() {
            let x = 42;
            unresolved_macro!("abc", x);
        }
    """
    )

    fun `test never type`() = checkByText(
        """
        fn noreturn(_a: i32) -> ! { panic!() }

        fn main() {
            let x = 42;
            noreturn(x);
        }
    """
    )

    fun `test if else`() = checkByText(
        """
        fn main() {
            let x = 42;
            if abc {
            } else if x {
            }
        }
    """
    )

    fun `test try expr`() = checkByText(
        """
        fn foo() {
            let x = 42;
            bar(x)?
        }
    """
    )

    fun `test closure`() = checkByText(
        """
        fn foo() {
            let x = 42;
            bar(move || {
                let y = x;
                let <warning descr="Variable `z` is never used">z</warning> = y;
            })
        }
    """
    )

    fun `test block`() = checkByText(
        """
        fn foo() {
            let x = 42;
            bar({
                let y = x;
                let <warning descr="Variable `z` is never used">z</warning> = y;
            })
        }
    """
    )

    fun `test infinite loop`() = checkByText(
        """
        fn foo() {
            let x = 42;
            let <warning descr="Variable `y` is never used">y</warning> = x;
            loop {
                println!("Hello");
            }
        }
    """
    )

    fun `test continue in loop`() = checkByText(
        """
        fn foo() {
            loop {
                if something {
                    let x = 42;
                    let <warning descr="Variable `y` is never used">y</warning> = x;
                    continue;
                }
    
                return;
            }
        }
    """
    )
}
