/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.cfg

import junit.framework.ComparisonFailure
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.types.regions.getRegionScopeTree

class RsControlFlowGraphTest : RsTestBase() {
    fun `test empty block`() = testCFG("""
        fn main() {}
    """, """
        Entry
        BLOCK
        Exit
        Termination
    """)

    fun `test straightforward`() = testCFG("""
        fn main() {
            let x = (1 + 2) as i32;
            let arr = [0, 5 * 7 + x];
            let mut y = -arr[x + 10];
            { y = 10; y += x; };
            f(x, y);
            y += x;
        }
    """, """
        Entry
        1
        2
        1 + 2
        (1 + 2) as i32
        x
        x
        let x = (1 + 2) as i32;
        0
        5
        7
        5 * 7
        x
        5 * 7 + x
        [0, 5 * 7 + x]
        arr
        arr
        let arr = [0, 5 * 7 + x];
        arr
        x
        10
        x + 10
        arr[x + 10]
        -arr[x + 10]
        mut y
        mut y
        let mut y = -arr[x + 10];
        y
        10
        y = 10
        y = 10;
        y
        x
        y += x
        y += x;
        BLOCK
        BLOCK
        BLOCK;
        f
        x
        y
        f(x, y)
        f(x, y);
        y
        x
        y += x
        y += x;
        BLOCK
        Exit
        Termination
    """)

    fun `test if`() = testCFG("""
        fn foo() {
            if true { 1 };
        }
    """, """
        Entry
        true
        1
        BLOCK
        IF
        IF;
        BLOCK
        Exit
        Termination
    """)

    fun `test if else`() = testCFG("""
        fn foo() {
            if true { 1 } else if false { 2 } else { 3 };
        }
    """, """
        Entry
        true
        1
        BLOCK
        IF
        IF;
        BLOCK
        Exit
        Termination
        false
        2
        BLOCK
        IF
        3
        BLOCK
    """)

    fun `test if let`() = testCFG("""
        fn foo() {
            if let Some(s) = x { 1 };
        }
    """, """
        Entry
        x
        s
        s
        Some(s)
        Dummy
        1
        BLOCK
        IF
        IF;
        BLOCK
        Exit
        Termination
    """)

    fun `test if let else`() = testCFG("""
        fn foo() {
            if let Some(s) = x { 1 } else { 2 };
        }
    """, """
        Entry
        x
        s
        s
        Some(s)
        Dummy
        1
        BLOCK
        IF
        IF;
        BLOCK
        Exit
        Termination
        2
        BLOCK
    """)

    fun `test if let or patterns`() = testCFG("""
        fn foo() {
            if let A(s) | B(s) = x { 1 };
        }
    """, """
        Entry
        x
        s
        s
        A(s)
        Dummy
        1
        BLOCK
        s
        s
        B(s)
        IF
        IF;
        BLOCK
        Exit
        Termination
    """)

    fun `test if else with unreachable`() = testCFG("""
        fn main() {
            let x = 1;
            if x > 0 && x < 10 { return; } else { return; }
            let y = 2;
        }
    """, """
        Entry
        1
        x
        x
        let x = 1;
        x
        0
        x > 0
        x
        10
        x < 10
        x > 0 && x < 10
        return
        Exit
        Termination
        return
    """)

    fun `test loop`() = testCFG("""
        fn main() {
            loop {
                x += 1;
            }
            y;
        }
    """, """
        Entry
        Dummy
        x
        1
        x += 1
        x += 1;
        BLOCK
        Termination
    """)

    fun `test while`() = testCFG("""
        fn main() {
            let mut x = 1;

            while x < 5 {
                x += 1;
                if x > 3 { return; }
            }
        }
    """, """
        Entry
        1
        mut x
        mut x
        let mut x = 1;
        Dummy
        x
        5
        x < 5
        WHILE
        BLOCK
        Exit
        Termination
        x
        1
        x += 1
        x += 1;
        x
        3
        x > 3
        return
        IF
        BLOCK
    """)

    fun `test while with break`() = testCFG("""
        fn main() {
            while cond1 {
                op1;
                if cond2 { break; }
                op2;
            }
        }
    """, """
        Entry
        Dummy
        cond1
        WHILE
        BLOCK
        Exit
        Termination
        op1
        op1;
        cond2
        break
        IF
        IF;
        op2
        op2;
        BLOCK
    """)

    fun `test while with labeled break`() = testCFG("""
        fn main() {
            'loop: while cond1 {
                op1;
                loop {
                    if cond2 { break 'loop; }
                }
                op2;
            }
        }
    """, """
        Entry
        Dummy
        cond1
        WHILE
        BLOCK
        Exit
        Termination
        op1
        op1;
        Dummy
        cond2
        break 'loop
        IF
        BLOCK
    """)

    fun `test while with continue`() = testCFG("""
        fn main() {
            while cond1 {
                op1;
                if cond2 { continue; }
                op2;
            }
        }
    """, """
        Entry
        Dummy
        cond1
        WHILE
        BLOCK
        Exit
        Termination
        op1
        op1;
        cond2
        continue
        IF
        IF;
        op2
        op2;
        BLOCK
    """)

    fun `test while let`() = testCFG("""
        fn main() {
            while let x = f() {
                1;
            }
        }
    """, """
        Entry
        Dummy
        f
        f()
        WHILE
        BLOCK
        Exit
        Termination
        x
        x
        Dummy
        1
        1;
        BLOCK
    """)

    fun `test while let or patterns`() = testCFG("""
        fn main() {
            while let A(s) | B(s) = x {
                1;
            }
        }
    """, """
        Entry
        Dummy
        x
        WHILE
        BLOCK
        Exit
        Termination
        s
        s
        A(s)
        Dummy
        1
        1;
        BLOCK
        s
        s
        B(s)
    """)

    fun `test while with unreachable`() = testCFG("""
        fn main() {
            let mut x = 1;

            while x < 5 {
                x += 1;
                if x > 3 { return; } else { x += 10; return; }
                let z = 42;
            }

            let y = 2;
        }
    """, """
        Entry
        1
        mut x
        mut x
        let mut x = 1;
        Dummy
        x
        5
        x < 5
        WHILE
        WHILE;
        2
        y
        y
        let y = 2;
        BLOCK
        Exit
        Termination
        x
        1
        x += 1
        x += 1;
        x
        3
        x > 3
        return
        x
        10
        x += 10
        x += 10;
        return
    """)

    fun `test for`() = testCFG("""
        fn main() {
            for i in x.foo(42) {
                for j in 0..x.bar.foo {
                    x += i;
                }
            }
            y;
        }
    """, """
        Entry
        x
        42
        x.foo(42)
        Dummy
        FOR
        FOR;
        y
        y;
        BLOCK
        Exit
        Termination
        i
        i
        0
        x
        x.bar
        x.bar.foo
        0..x.bar.foo
        Dummy
        FOR
        BLOCK
        j
        j
        x
        i
        x += i
        x += i;
        BLOCK
    """)

    fun `test for with break and continue`() = testCFG("""
        fn main() {
            for x in xs {
                op1;
                for y in ys {
                    op2;
                    if cond { continue; }
                    break;
                    op3;
                }
            }
            y;
        }
    """, """
        Entry
        xs
        Dummy
        FOR
        FOR;
        y
        y;
        BLOCK
        Exit
        Termination
        x
        x
        op1
        op1;
        ys
        Dummy
        FOR
        BLOCK
        y
        y
        op2
        op2;
        cond
        continue
        IF
        IF;
        break
    """)

    fun `test match`() = testCFG("""
        enum E { A, B(i32), C }
        fn main() {
            let x = E::A;
            match x {
                E::A => 1,
                E::B(x) => match x { 0...10 => 2, _ => 3 },
                E::C => 4
            };
            let y = 0;
        }
    """, """
        Entry
        E::A
        x
        x
        let x = E::A;
        x
        E::A
        Dummy
        1
        MATCH
        MATCH;
        0
        y
        y
        let y = 0;
        BLOCK
        Exit
        Termination
        x
        x
        E::B(x)
        Dummy
        x
        0...10
        Dummy
        2
        MATCH
        _
        Dummy
        3
        E::C
        Dummy
        4
    """)

    fun `test match 1`() = testCFG("""
        enum E { A(i32), B }
        fn main() {
            let x = E::A(1);
            match x {
                E::A(val) if val > 0 => val,
                E::B => return,
            };
            let y = 0;
        }
    """, """
        Entry
        E::A
        1
        E::A(1)
        x
        x
        let x = E::A(1);
        x
        val
        val
        E::A(val)
        Dummy
        val
        0
        val > 0
        if val > 0
        Dummy
        val
        MATCH
        MATCH;
        0
        y
        y
        let y = 0;
        BLOCK
        Exit
        Termination
        E::B
        Dummy
        return
    """)

    fun `test try`() = testCFG("""
        fn main() {
            x.foo(a, b)?;
            y;
        }
    """, """
        Entry
        x
        a
        b
        x.foo(a, b)
        Dummy
        Exit
        Termination
        x.foo(a, b)?
        x.foo(a, b)?;
        y
        y;
        BLOCK
    """)

    fun `test patterns`() = testCFG("""
        struct S { data1: i32, data2: i32 }

        fn main() {
            let x = S { data1: 42, data2: 24 };
            let S { data1: a, data2: b } = s;
            let (x, (y, z)) = (1, (2, 3));
            [0, 1 + a];
        }
    """, """
        Entry
        42
        24
        S { data1: 42, data2: 24 }
        x
        x
        let x = S { data1: 42, data2: 24 };
        s
        a
        a
        b
        b
        S { data1: a, data2: b }
        let S { data1: a, data2: b } = s;
        1
        2
        3
        (2, 3)
        (1, (2, 3))
        x
        x
        y
        y
        z
        z
        (y, z)
        (x, (y, z))
        let (x, (y, z)) = (1, (2, 3));
        0
        1
        a
        1 + a
        [0, 1 + a]
        [0, 1 + a];
        BLOCK
        Exit
        Termination
    """)

    fun `test noreturn simple`() = testCFG("""
        fn main() {
            if true {
                noreturn();
            }
            42;
        }

        fn noreturn() -> ! { panic!() }
    """, """
        Entry
        true
        noreturn
        noreturn()
        Termination
        IF
        IF;
        42
        42;
        BLOCK
        Exit
    """)

    fun `test noreturn complex expr`() = testCFG("""
        fn main() {
            if true {
                foo.bar(1, noreturn());
            }
            42;
        }

        fn noreturn() -> ! { panic!() }
    """, """
        Entry
        true
        foo
        1
        noreturn
        noreturn()
        Termination
        IF
        IF;
        42
        42;
        BLOCK
        Exit
    """)

    fun `test panic macro call inside stmt`() = testCFG("""
        fn main() {
            1;
            if true { 2; } else { panic!(); }
            42;
        }
    """, """
        Entry
        1
        1;
        true
        2
        2;
        BLOCK
        IF
        IF;
        42
        42;
        BLOCK
        Exit
        Termination
        panic!()
    """)

    fun `test panic macro call outside stmt`() = testCFG("""
        fn main() {
            match x {
                true => 2,
                false => panic!()
            };
        }
    """, """
        Entry
        x
        true
        Dummy
        2
        MATCH
        MATCH;
        BLOCK
        Exit
        Termination
        false
        Dummy
        panic!()
    """)

    fun `test macro call outside stmt`() = testCFG("""
        fn main() {
            match e {
                E::A => 2,
                E::B => some_macro!()
            };
        }
    """, """
        Entry
        e
        E::A
        Dummy
        2
        MATCH
        MATCH;
        BLOCK
        Exit
        Termination
        E::B
        Dummy
        some_macro!()
    """)

    fun `test shorthand struct literal`() = testCFG("""
        struct S { x: i32 }
        
        fn foo(x: i32) {
            S { x };
        }
    """, """
        Entry
        x
        S { x }
        S { x };
        BLOCK
        Exit
        Termination
    """)

    fun `test lambda expr`() = testCFG("""
        fn foo() {
            let f = |x: i32| { x + 1 };
        }
    """, """
        Entry
        x
        1
        x + 1
        BLOCK
        BLOCK
        |x: i32| { x + 1 }
        f
        f
        let f = |x: i32| { x + 1 };
        BLOCK
        Exit
        Termination
    """)

    fun `test arbitrary macro call`() = testCFG("""
        macro_rules! my_macro {
            ($ e1:expr, $ e2:expr) => ($ e1 + $ e2);
        }

        fn main() {
            my_macro!(x, y);
        }
    """, """
        Entry
        x
        y
        x + y
        BLOCK
        Exit
        Termination
    """)

    fun `test println! macro call`() = testCFG("""
        fn main() {
            println!("{} {}", x, y);
        }
    """, """
        Entry
        "{} {}"
        x
        y
        println!("{} {}", x, y)
        println!("{} {}", x, y);
        BLOCK
        Exit
        Termination
    """)

    fun `test vec! macro call`() = testCFG("""
        fn main() {
            vec![ S { x }, s1 ];
        }
    """, """
        Entry
        x
        S { x }
        s1
        vec![ S { x }, s1 ]
        vec![ S { x }, s1 ];
        BLOCK
        Exit
        Termination
    """)

    fun `test assert_eq! macro call`() = testCFG("""
        fn main() {
            assert_eq!(x, y);
        }
    """, """
        Entry
        x
        y
        assert_eq!(x, y)
        assert_eq!(x, y);
        BLOCK
        Exit
        Termination
    """)

    private fun testCFG(@Language("Rust") code: String, expectedIndented: String) {
        InlineFile(code)
        val function = myFixture.file.descendantsOfType<RsFunction>().firstOrNull() ?: return
        val cfg = ControlFlowGraph.buildFor(function.block!!, getRegionScopeTree(function))
        val expected = expectedIndented.trimIndent()
        val actual = cfg.graph.depthFirstTraversalTrace(cfg.entry)
        check(actual == expected) { throw ComparisonFailure("Comparision failed", expected, actual) }
    }
}
