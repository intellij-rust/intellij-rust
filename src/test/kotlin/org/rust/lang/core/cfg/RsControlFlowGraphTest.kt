/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.cfg

import junit.framework.ComparisonFailure
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.ext.descendantsOfType

class RsControlFlowGraphTest : RsTestBase() {
    fun `test empty block`() = testCFG("""
        fn main() {}
    """, """
        Entry
        BLOCK
        Exit
    """
    )

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
        let x = (1 + 2) as i32;
        0
        5
        7
        5 * 7
        x
        5 * 7 + x
        [0, 5 * 7 + x]
        arr
        let arr = [0, 5 * 7 + x];
        arr
        x
        10
        x + 10
        arr[x + 10]
        -arr[x + 10]
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
    """
    )

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
        return
    """
    )

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
    """
    )

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
        let mut x = 1;
        Dummy
        x
        5
        x < 5
        WHILE
        BLOCK
        Exit
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
    """
    )

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
        let mut x = 1;
        Dummy
        x
        5
        x < 5
        WHILE
        WHILE;
        2
        y
        let y = 2;
        BLOCK
        Exit
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
    """
    )

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
        Dummy
        x
        42
        x.foo(42)
        FOR
        FOR;
        y
        y;
        BLOCK
        Exit
        Dummy
        0
        x
        x.bar
        x.bar.foo
        0..x.bar.foo
        FOR
        BLOCK
        x
        i
        x += i
        x += i;
        BLOCK
    """
    )

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
        let x = E::A;
        x
        E::A
        Dummy
        1
        MATCH
        MATCH;
        0
        y
        let y = 0;
        BLOCK
        Exit
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
    """
    )

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
        let x = E::A(1);
        x
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
        let y = 0;
        BLOCK
        Exit
        E::B
        Dummy
        return
    """
    )

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
        x.foo(a, b)?
        x.foo(a, b)?;
        y
        y;
        BLOCK
    """
    )

    fun `test patterns`() = testCFG("""
        struct S { data: i32 }

        fn main() {
            let x = S { data: 42 };
            let S { data: a } = s;
            let (x, (y, z)) = (1, (2, 3));
            [0, 1 + a];
        }
    """, """
        Entry
        x
        let x = S { data: 42 };
        s
        a
        S { data: a }
        let S { data: a } = s;
        1
        2
        3
        (2, 3)
        (1, (2, 3))
        x
        y
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
    """
    )

    protected fun testCFG(@Language("Rust") code: String, expectedIndented: String) {
        InlineFile(code)
        val block = myFixture.file.descendantsOfType<RsBlock>().firstOrNull() ?: return
        val cfg = ControlFlowGraph.buildFor(block)
        val expected = expectedIndented.trimIndent()
        val actual = cfg.depthFirstTraversalTrace()
        check(actual == expected) { throw ComparisonFailure("Comparision failed", expected, actual) }
    }
}
