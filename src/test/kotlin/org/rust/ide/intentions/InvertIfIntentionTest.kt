/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class InvertIfIntentionTest : RsIntentionTestBase(InvertIfIntention::class) {
    fun `test if let unavailable`() = doUnavailableTest("""
        fn foo(a: Option<i32>) {
            if/*caret*/ let Some(x) = a {} else {}
        }
    """)

    fun `test if without condition unavailable`() = doUnavailableTest("""
        fn foo() {
            if /*caret*/ {} else {}
        }
    """)

    fun `test if without then branch unavailable`() = doUnavailableTest("""
        fn foo(a: i32) {
            if a/*caret*/ == 10 else {}
        }
    """)

    fun `test availability range`() = checkAvailableInSelectionOnly("""
        fn foo() {
            <selection>if</selection> 2 == 2 { Ok(()) } else { Err(()) }
        }
    """)

    fun `test simple inversion`() = doAvailableTest("""
        fn foo() {
            if/*caret*/ 2 == 2 { Ok(()) } else { Err(()) }
        }
    """, """
        fn foo() {
            if 2 != 2 { Err(()) } else { Ok(()) }
        }
    """)

    // `!(2 == 2)` can be later simplified to `2 != 2` by user via `Simplify boolean expression` intention
    fun `test simple inversion parens`() = doAvailableTest("""
        fn foo() {
            if/*caret*/ (2 == 2) { Ok(()) } else { Err(()) }
        }
    """, """
        fn foo() {
            if !(2 == 2) { Err(()) } else { Ok(()) }
        }
    """)

    fun `test conjunction condition`() = doAvailableTest("""
        fn foo() {
            if/*caret*/ 2 == 2 && 3 == 3 { Ok(()) } else { Err(()) }
        }
    """, """
        fn foo() {
            if 2 != 2 || 3 != 3 { Err(()) } else { Ok(()) }
        }
    """)

    fun `test complex condition`() = doAvailableTest("""
        fn foo() {
            if/*caret*/ 2 == 2 && (3 == 3 || 4 == 4) && (5 == 5) { Ok(()) } else { Err(()) }
        }
    """, """
        fn foo() {
            if 2 != 2 || !(3 == 3 || 4 == 4) || (5 != 5) { Err(()) } else { Ok(()) }
        }
    """)

    fun `test bool literal inversion`() = doAvailableTest("""
        fn foo() {
            if/*caret*/ true { Ok(()) } else { Err(()) }
        }
    """, """
        fn foo() {
            if false { Err(()) } else { Ok(()) }
        }
    """)

    fun `test simple inversion strange formatting`() = doAvailableTest("""
        fn foo() {
            if/*caret*/ 2 == 2 {
                Ok(())
            } else { Err(()) }
        }
    """, """
        fn foo() {
            if 2 != 2 { Err(()) } else {
                Ok(())
            }
        }
    """)

    fun `test very path expr condition`() = doAvailableTest("""
        fn foo(cond: bool) {
            if/*caret*/ cond {
                Ok(())
            } else {
                Err(())
            }
        }
    """, """
        fn foo(cond: bool) {
            if !cond {
                Err(())
            } else {
                Ok(())
            }
        }
    """)

    fun `test if without else in function 1`() = doAvailableSymmetricTest("""
        fn foo(f: bool) {
            /*caret*/if f {
                println!("1");
            }
        }
    """, """
        fn foo(f: bool) {
            /*caret*/if !f {
                return;
            }
            println!("1");
        }
    """)

    fun `test if without else in function 2`() = doAvailableTest("""
        fn foo(f: bool) -> i32 {
            /*caret*/if f { return 1; }
            0
        }
    """, """
        fn foo(f: bool) -> i32 {
            if !f { return 0; }
            1
        }
    """)

    fun `test if without else in function 3`() = doAvailableTest("""
        fn foo(f: bool) -> i32 {
            /*caret*/if f { return 1; }
            return 0;
        }
    """, """
        fn foo(f: bool) -> i32 {
            if !f { return 0; }
            1
        }
    """)

    fun `test if without else in function 4`() = doUnavailableTest("""
        fn foo(f: bool) -> i32 {
            /*caret*/if f { println!("1"); }
            0
        }
    """)

    fun `test if without else in function 5`() = doUnavailableTest("""
        fn foo(f: bool, g: bool) -> i32 {
            /*caret*/if f {
                if g { return 1; }
            }
            0
        }
    """)

    fun `test if without else nested 1`() = doAvailableSymmetricTest("""
        fn foo(f: bool, g: bool) -> i32 {
            if f {
                /*caret*/if g {
                    return 1;
                }
                return 2;
            }
            0
        }
    """, """
        fn foo(f: bool, g: bool) -> i32 {
            if f {
                /*caret*/if !g {
                    return 2;
                }
                return 1;
            }
            0
        }
    """)

    fun `test if without else nested 2`() = doUnavailableTest("""
        fn foo(f: bool, g: bool) -> i32 {
            if f {
                /*caret*/if g {
                    return 1;
                }
            }
            0
        }
    """)

    fun `test if without else in loop 1`() = doAvailableSymmetricTest("""
        fn foo() {
            for f in [false, true] {
                /*caret*/if f {
                    println("1");
                }
            }
        }
    """, """
        fn foo() {
            for f in [false, true] {
                /*caret*/if !f {
                    continue;
                }
                println("1");
            }
        }
    """)

    fun `test if without else in loop 2`() = doAvailableTest("""
        fn foo() {
            for f in [false, true] {
                /*caret*/if f {
                    println("1");
                    continue;
                }
                println("2");
            }
        }
    """, """
        fn foo() {
            for f in [false, true] {
                if !f {
                    println("2");
                    continue;
                }
                println("1");
            }
        }
    """)

    fun `test if without else in loop 3`() = doUnavailableTest("""
        fn foo() {
            for f in [false, true] {
                /*caret*/if f {
                    println("1");
                }
                println("2");
            }
        }
    """)

    fun `test if without else in loop 4`() = doAvailableTest("""
        fn foo() -> i32 {
            for f in [false, true] {
                /*caret*/if f {
                    return 1;
                }
                return 2;
            }
        }
    """, """
        fn foo() -> i32 {
            for f in [false, true] {
                if !f {
                    return 2;
                }
                return 1;
            }
        }
    """)

    fun `test if without else with labeled break`() = doAvailableTest("""
        fn foo() {
            'outer: loop {
                'inner: loop {
                    /*caret*/if f {
                        println!("1");
                        break 'outer;
                    }
                }
            }
        }
    """, """
        fn foo() {
            'outer: loop {
                'inner: loop {
                    if !f {
                        continue;
                    }
                    println!("1");
                    break 'outer;
                }
            }
        }
    """)

    fun `test if without else inside match 1`() = doAvailableTest("""
        fn foo() {
            let x = match y {
                0 => 1,
                _ => {
                    /*caret*/if f {
                        return 1;
                    }
                    return 2;
                }
            };
        }
    """, """
        fn foo() {
            let x = match y {
                0 => 1,
                _ => {
                    if !f {
                        return 2;
                    }
                    return 1;
                }
            };
        }
    """)

    fun `test if without else inside match 2`() = doUnavailableTest("""
        fn foo() {
            let x = match y {
                0 => 1,
                _ => {
                    /*caret*/if f {
                        return;
                    }
                    3
                }
            };
        }
    """)
}
