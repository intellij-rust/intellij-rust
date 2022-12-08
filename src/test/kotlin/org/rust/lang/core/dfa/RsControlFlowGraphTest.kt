/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa

import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.lang.core.macros.MacroExpansionManager
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.block
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.types.regions.getRegionScopeTree

// The graphs can be rendered right inside the IDE using the DOT Language plugin
// https://plugins.jetbrains.com/plugin/10312-dot-language
class RsControlFlowGraphTest : RsTestBase() {
    fun `test empty block`() = testCFG("""
        fn main() {}
    """, """
        digraph {
            "0: Entry" -> "3: BLOCK";
            "3: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test straightforward`() = testCFG("""
        fn main() {
            let x = (1 + 2) as i32;
            let x = (;
            let arr = [0, 5 * 7 + x];
            let mut y = -arr[x + 10];
            { y = 10; y += x; };
            f(x, y);
            y += x;
        }
    """, """
        digraph {
            "0: Entry" -> "3: 1";
            "3: 1" -> "4: 2";
            "4: 2" -> "5: 1 + 2";
            "5: 1 + 2" -> "6: (1 + 2)";
            "6: (1 + 2)" -> "7: (1 + 2) as i32";
            "7: (1 + 2) as i32" -> "8: x";
            "8: x" -> "9: x";
            "9: x" -> "10: let x = (1 + 2) as i32;";
            "10: let x = (1 + 2) as i32;" -> "11: (";
            "11: (" -> "12: x";
            "12: x" -> "13: x";
            "13: x" -> "14: let x = (;";
            "14: let x = (;" -> "15: 0";
            "15: 0" -> "16: 5";
            "16: 5" -> "17: 7";
            "17: 7" -> "18: 5 * 7";
            "18: 5 * 7" -> "19: x";
            "19: x" -> "20: 5 * 7 + x";
            "20: 5 * 7 + x" -> "21: [0, 5 * 7 + x]";
            "21: [0, 5 * 7 + x]" -> "22: arr";
            "22: arr" -> "23: arr";
            "23: arr" -> "24: let arr = [0, 5 * 7 + x];";
            "24: let arr = [0, 5 * 7 + x];" -> "25: arr";
            "25: arr" -> "26: x";
            "26: x" -> "27: 10";
            "27: 10" -> "28: x + 10";
            "28: x + 10" -> "29: arr[x + 10]";
            "29: arr[x + 10]" -> "30: -arr[x + 10]";
            "30: -arr[x + 10]" -> "31: mut y";
            "31: mut y" -> "32: mut y";
            "32: mut y" -> "33: let mut y = -arr[x + 10];";
            "33: let mut y = -arr[x + 10];" -> "35: y";
            "35: y" -> "36: 10";
            "36: 10" -> "37: y = 10";
            "37: y = 10" -> "38: y = 10;";
            "38: y = 10;" -> "39: y";
            "39: y" -> "40: x";
            "40: x" -> "41: y += x";
            "41: y += x" -> "42: y += x;";
            "42: y += x;" -> "43: BLOCK";
            "43: BLOCK" -> "34: BLOCK";
            "34: BLOCK" -> "44: BLOCK;";
            "44: BLOCK;" -> "45: f";
            "45: f" -> "46: x";
            "46: x" -> "47: y";
            "47: y" -> "48: f(x, y)";
            "48: f(x, y)" -> "49: f(x, y);";
            "49: f(x, y);" -> "50: y";
            "50: y" -> "51: x";
            "51: x" -> "52: y += x";
            "52: y += x" -> "53: y += x;";
            "53: y += x;" -> "54: BLOCK";
            "54: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test if`() = testCFG("""
        fn foo() {
            if true { 1 };
        }
    """, """
        digraph {
            "0: Entry" -> "3: true";
            "3: true" -> "4: 1";
            "4: 1" -> "5: BLOCK";
            "3: true" -> "6: IF";
            "5: BLOCK" -> "6: IF";
            "6: IF" -> "7: IF;";
            "7: IF;" -> "8: BLOCK";
            "8: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test if else`() = testCFG("""
        fn foo() {
            if true { 1 } else if false { 2 } else { 3 };
        }
    """, """
        digraph {
            "0: Entry" -> "3: true";
            "3: true" -> "4: 1";
            "4: 1" -> "5: BLOCK";
            "3: true" -> "6: false";
            "6: false" -> "7: 2";
            "7: 2" -> "8: BLOCK";
            "6: false" -> "9: 3";
            "9: 3" -> "10: BLOCK";
            "8: BLOCK" -> "11: IF";
            "10: BLOCK" -> "11: IF";
            "5: BLOCK" -> "12: IF";
            "11: IF" -> "12: IF";
            "12: IF" -> "13: IF;";
            "13: IF;" -> "14: BLOCK";
            "14: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test if let`() = testCFG("""
        fn foo() {
            if let Some(s) = x { 1 };
        }
    """, """
        digraph {
            "0: Entry" -> "3: x";
            "3: x" -> "5: s";
            "5: s" -> "6: s";
            "6: s" -> "7: Some(s)";
            "7: Some(s)" -> "4: Dummy";
            "4: Dummy" -> "8: 1";
            "8: 1" -> "9: BLOCK";
            "3: x" -> "10: IF";
            "9: BLOCK" -> "10: IF";
            "10: IF" -> "11: IF;";
            "11: IF;" -> "12: BLOCK";
            "12: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test if let chain`() = testCFG("""
        fn foo() {
            if let Some(s) = x && let Some(u) = y { 1 };
        }
    """, """
        digraph {
            "0: Entry" -> "3: x";
            "3: x" -> "4: s";
            "4: s" -> "5: s";
            "5: s" -> "6: Some(s)";
            "6: Some(s)" -> "7: let Some(s) = x";
            "7: let Some(s) = x" -> "8: y";
            "8: y" -> "9: u";
            "9: u" -> "10: u";
            "10: u" -> "11: Some(u)";
            "11: Some(u)" -> "12: let Some(u) = y";
            "7: let Some(s) = x" -> "13: let Some(s) = x && let Some(u) = y";
            "12: let Some(u) = y" -> "13: let Some(s) = x && let Some(u) = y";
            "13: let Some(s) = x && let Some(u) = y" -> "14: 1";
            "14: 1" -> "15: BLOCK";
            "13: let Some(s) = x && let Some(u) = y" -> "16: IF";
            "15: BLOCK" -> "16: IF";
            "16: IF" -> "17: IF;";
            "17: IF;" -> "18: BLOCK";
            "18: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test if let else`() = testCFG("""
        fn foo() {
            if let Some(s) = x { 1 } else { 2 };
        }
    """, """
        digraph {
            "0: Entry" -> "3: x";
            "3: x" -> "5: s";
            "5: s" -> "6: s";
            "6: s" -> "7: Some(s)";
            "7: Some(s)" -> "4: Dummy";
            "4: Dummy" -> "8: 1";
            "8: 1" -> "9: BLOCK";
            "3: x" -> "10: 2";
            "10: 2" -> "11: BLOCK";
            "9: BLOCK" -> "12: IF";
            "11: BLOCK" -> "12: IF";
            "12: IF" -> "13: IF;";
            "13: IF;" -> "14: BLOCK";
            "14: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test if let chain else`() = testCFG("""
        fn foo() {
            if let Some(s) = x && let Some(u) = y { 1 } else { 2 };
        }
    """, """
        digraph {
            "0: Entry" -> "3: x";
            "3: x" -> "4: s";
            "4: s" -> "5: s";
            "5: s" -> "6: Some(s)";
            "6: Some(s)" -> "7: let Some(s) = x";
            "7: let Some(s) = x" -> "8: y";
            "8: y" -> "9: u";
            "9: u" -> "10: u";
            "10: u" -> "11: Some(u)";
            "11: Some(u)" -> "12: let Some(u) = y";
            "7: let Some(s) = x" -> "13: let Some(s) = x && let Some(u) = y";
            "12: let Some(u) = y" -> "13: let Some(s) = x && let Some(u) = y";
            "13: let Some(s) = x && let Some(u) = y" -> "14: 1";
            "14: 1" -> "15: BLOCK";
            "13: let Some(s) = x && let Some(u) = y" -> "16: 2";
            "16: 2" -> "17: BLOCK";
            "15: BLOCK" -> "18: IF";
            "17: BLOCK" -> "18: IF";
            "18: IF" -> "19: IF;";
            "19: IF;" -> "20: BLOCK";
            "20: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test if let or patterns`() = testCFG("""
        fn foo() {
            if let A(s) | B(s) = x { 1 };
        }
    """, """
        digraph {
            "0: Entry" -> "3: x";
            "3: x" -> "5: s";
            "5: s" -> "6: s";
            "6: s" -> "7: A(s)";
            "7: A(s)" -> "4: Dummy";
            "3: x" -> "8: s";
            "8: s" -> "9: s";
            "9: s" -> "10: B(s)";
            "10: B(s)" -> "4: Dummy";
            "4: Dummy" -> "11: 1";
            "11: 1" -> "12: BLOCK";
            "3: x" -> "13: IF";
            "12: BLOCK" -> "13: IF";
            "13: IF" -> "14: IF;";
            "14: IF;" -> "15: BLOCK";
            "15: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test if else with unreachable`() = testCFG("""
        fn main() {
            let x = 1;
            if x > 0 && x < 10 { return; } else { return; }
            let y = 2;
        }
    """, """
        digraph {
            "0: Entry" -> "3: 1";
            "3: 1" -> "4: x";
            "4: x" -> "5: x";
            "5: x" -> "6: let x = 1;";
            "6: let x = 1;" -> "7: x";
            "7: x" -> "8: 0";
            "8: 0" -> "9: x > 0";
            "9: x > 0" -> "10: x";
            "10: x" -> "11: 10";
            "11: 10" -> "12: x < 10";
            "9: x > 0" -> "13: x > 0 && x < 10";
            "12: x < 10" -> "13: x > 0 && x < 10";
            "13: x > 0 && x < 10" -> "14: return";
            "14: return" -> "1: Exit";
            "15: Unreachable" -> "16: return;";
            "16: return;" -> "17: BLOCK";
            "13: x > 0 && x < 10" -> "18: return";
            "18: return" -> "1: Exit";
            "19: Unreachable" -> "20: return;";
            "20: return;" -> "21: BLOCK";
            "17: BLOCK" -> "22: IF";
            "21: BLOCK" -> "22: IF";
            "22: IF" -> "23: IF;";
            "23: IF;" -> "24: 2";
            "24: 2" -> "25: y";
            "25: y" -> "26: y";
            "26: y" -> "27: let y = 2;";
            "27: let y = 2;" -> "28: BLOCK";
            "28: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test loop`() = testCFG("""
        fn main() {
            loop {
                x += 1;
            }
            y;
        }
    """, """
        digraph {
            "0: Entry" -> "3: Dummy";
            "3: Dummy" -> "5: x";
            "5: x" -> "6: 1";
            "6: 1" -> "7: x += 1";
            "7: x += 1" -> "8: x += 1;";
            "8: x += 1;" -> "9: BLOCK";
            "9: BLOCK" -> "3: Dummy";
            "3: Dummy" -> "2: Termination";
            "4: LOOP" -> "10: LOOP;";
            "10: LOOP;" -> "11: y";
            "11: y" -> "12: y;";
            "12: y;" -> "13: BLOCK";
            "13: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
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
        digraph {
            "0: Entry" -> "3: 1";
            "3: 1" -> "4: mut x";
            "4: mut x" -> "5: mut x";
            "5: mut x" -> "6: let mut x = 1;";
            "6: let mut x = 1;" -> "7: Dummy";
            "7: Dummy" -> "9: x";
            "9: x" -> "10: 5";
            "10: 5" -> "11: x < 5";
            "11: x < 5" -> "8: WHILE";
            "11: x < 5" -> "12: x";
            "12: x" -> "13: 1";
            "13: 1" -> "14: x += 1";
            "14: x += 1" -> "15: x += 1;";
            "15: x += 1;" -> "16: x";
            "16: x" -> "17: 3";
            "17: 3" -> "18: x > 3";
            "18: x > 3" -> "19: return";
            "19: return" -> "1: Exit";
            "20: Unreachable" -> "21: return;";
            "21: return;" -> "22: BLOCK";
            "18: x > 3" -> "23: IF";
            "22: BLOCK" -> "23: IF";
            "23: IF" -> "24: BLOCK";
            "24: BLOCK" -> "7: Dummy";
            "8: WHILE" -> "25: BLOCK";
            "25: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
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
        digraph {
            "0: Entry" -> "3: Dummy";
            "3: Dummy" -> "5: cond1";
            "5: cond1" -> "4: WHILE";
            "5: cond1" -> "6: op1";
            "6: op1" -> "7: op1;";
            "7: op1;" -> "8: cond2";
            "8: cond2" -> "9: break";
            "9: break" -> "4: WHILE";
            "9: break" -> "2: Termination";
            "10: Unreachable" -> "11: break;";
            "11: break;" -> "12: BLOCK";
            "8: cond2" -> "13: IF";
            "12: BLOCK" -> "13: IF";
            "13: IF" -> "14: IF;";
            "14: IF;" -> "15: op2";
            "15: op2" -> "16: op2;";
            "16: op2;" -> "17: BLOCK";
            "17: BLOCK" -> "3: Dummy";
            "4: WHILE" -> "18: BLOCK";
            "18: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
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
        digraph {
            "0: Entry" -> "3: Dummy";
            "3: Dummy" -> "5: cond1";
            "5: cond1" -> "4: WHILE";
            "5: cond1" -> "6: op1";
            "6: op1" -> "7: op1;";
            "7: op1;" -> "8: Dummy";
            "8: Dummy" -> "10: cond2";
            "10: cond2" -> "11: break 'loop";
            "11: break 'loop" -> "4: WHILE";
            "11: break 'loop" -> "2: Termination";
            "12: Unreachable" -> "13: break 'loop;";
            "13: break 'loop;" -> "14: BLOCK";
            "10: cond2" -> "15: IF";
            "14: BLOCK" -> "15: IF";
            "15: IF" -> "16: BLOCK";
            "16: BLOCK" -> "8: Dummy";
            "8: Dummy" -> "2: Termination";
            "9: LOOP" -> "17: LOOP;";
            "17: LOOP;" -> "18: op2";
            "18: op2" -> "19: op2;";
            "19: op2;" -> "20: BLOCK";
            "20: BLOCK" -> "3: Dummy";
            "4: WHILE" -> "21: BLOCK";
            "21: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
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
        digraph {
            "0: Entry" -> "3: Dummy";
            "3: Dummy" -> "5: cond1";
            "5: cond1" -> "4: WHILE";
            "5: cond1" -> "6: op1";
            "6: op1" -> "7: op1;";
            "7: op1;" -> "8: cond2";
            "8: cond2" -> "9: continue";
            "9: continue" -> "3: Dummy";
            "9: continue" -> "2: Termination";
            "10: Unreachable" -> "11: continue;";
            "11: continue;" -> "12: BLOCK";
            "8: cond2" -> "13: IF";
            "12: BLOCK" -> "13: IF";
            "13: IF" -> "14: IF;";
            "14: IF;" -> "15: op2";
            "15: op2" -> "16: op2;";
            "16: op2;" -> "17: BLOCK";
            "17: BLOCK" -> "3: Dummy";
            "4: WHILE" -> "18: BLOCK";
            "18: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test while let`() = testCFG("""
        fn main() {
            while let x = f() {
                1;
            }
        }
    """, """
        digraph {
            "0: Entry" -> "3: Dummy";
            "3: Dummy" -> "5: f";
            "5: f" -> "6: f()";
            "6: f()" -> "4: WHILE";
            "6: f()" -> "8: x";
            "8: x" -> "9: x";
            "9: x" -> "7: Dummy";
            "7: Dummy" -> "10: 1";
            "10: 1" -> "11: 1;";
            "11: 1;" -> "12: BLOCK";
            "12: BLOCK" -> "3: Dummy";
            "4: WHILE" -> "13: BLOCK";
            "13: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test while let or patterns`() = testCFG("""
        fn main() {
            while let A(s) | B(s) = x {
                1;
            }
        }
    """, """
        digraph {
            "0: Entry" -> "3: Dummy";
            "3: Dummy" -> "5: x";
            "5: x" -> "4: WHILE";
            "5: x" -> "7: s";
            "7: s" -> "8: s";
            "8: s" -> "9: A(s)";
            "9: A(s)" -> "6: Dummy";
            "5: x" -> "10: s";
            "10: s" -> "11: s";
            "11: s" -> "12: B(s)";
            "12: B(s)" -> "6: Dummy";
            "6: Dummy" -> "13: 1";
            "13: 1" -> "14: 1;";
            "14: 1;" -> "15: BLOCK";
            "15: BLOCK" -> "3: Dummy";
            "4: WHILE" -> "16: BLOCK";
            "16: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
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
        digraph {
            "0: Entry" -> "3: 1";
            "3: 1" -> "4: mut x";
            "4: mut x" -> "5: mut x";
            "5: mut x" -> "6: let mut x = 1;";
            "6: let mut x = 1;" -> "7: Dummy";
            "7: Dummy" -> "9: x";
            "9: x" -> "10: 5";
            "10: 5" -> "11: x < 5";
            "11: x < 5" -> "8: WHILE";
            "11: x < 5" -> "12: x";
            "12: x" -> "13: 1";
            "13: 1" -> "14: x += 1";
            "14: x += 1" -> "15: x += 1;";
            "15: x += 1;" -> "16: x";
            "16: x" -> "17: 3";
            "17: 3" -> "18: x > 3";
            "18: x > 3" -> "19: return";
            "19: return" -> "1: Exit";
            "20: Unreachable" -> "21: return;";
            "21: return;" -> "22: BLOCK";
            "18: x > 3" -> "23: x";
            "23: x" -> "24: 10";
            "24: 10" -> "25: x += 10";
            "25: x += 10" -> "26: x += 10;";
            "26: x += 10;" -> "27: return";
            "27: return" -> "1: Exit";
            "28: Unreachable" -> "29: return;";
            "29: return;" -> "30: BLOCK";
            "22: BLOCK" -> "31: IF";
            "30: BLOCK" -> "31: IF";
            "31: IF" -> "32: IF;";
            "32: IF;" -> "33: 42";
            "33: 42" -> "34: z";
            "34: z" -> "35: z";
            "35: z" -> "36: let z = 42;";
            "36: let z = 42;" -> "37: BLOCK";
            "37: BLOCK" -> "7: Dummy";
            "8: WHILE" -> "38: WHILE;";
            "38: WHILE;" -> "39: 2";
            "39: 2" -> "40: y";
            "40: y" -> "41: y";
            "41: y" -> "42: let y = 2;";
            "42: let y = 2;" -> "43: BLOCK";
            "43: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
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
        digraph {
            "0: Entry" -> "4: x";
            "4: x" -> "5: 42";
            "5: 42" -> "6: x.foo(42)";
            "6: x.foo(42)" -> "7: Dummy";
            "7: Dummy" -> "3: FOR";
            "7: Dummy" -> "8: i";
            "8: i" -> "9: i";
            "9: i" -> "11: 0";
            "11: 0" -> "12: x";
            "12: x" -> "13: x.bar";
            "13: x.bar" -> "14: x.bar.foo";
            "14: x.bar.foo" -> "15: 0..x.bar.foo";
            "15: 0..x.bar.foo" -> "16: Dummy";
            "16: Dummy" -> "10: FOR";
            "16: Dummy" -> "17: j";
            "17: j" -> "18: j";
            "18: j" -> "19: x";
            "19: x" -> "20: i";
            "20: i" -> "21: x += i";
            "21: x += i" -> "22: x += i;";
            "22: x += i;" -> "23: BLOCK";
            "23: BLOCK" -> "16: Dummy";
            "10: FOR" -> "24: BLOCK";
            "24: BLOCK" -> "7: Dummy";
            "3: FOR" -> "25: FOR;";
            "25: FOR;" -> "26: y";
            "26: y" -> "27: y;";
            "27: y;" -> "28: BLOCK";
            "28: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
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
        digraph {
            "0: Entry" -> "4: xs";
            "4: xs" -> "5: Dummy";
            "5: Dummy" -> "3: FOR";
            "5: Dummy" -> "6: x";
            "6: x" -> "7: x";
            "7: x" -> "8: op1";
            "8: op1" -> "9: op1;";
            "9: op1;" -> "11: ys";
            "11: ys" -> "12: Dummy";
            "12: Dummy" -> "10: FOR";
            "12: Dummy" -> "13: y";
            "13: y" -> "14: y";
            "14: y" -> "15: op2";
            "15: op2" -> "16: op2;";
            "16: op2;" -> "17: cond";
            "17: cond" -> "18: continue";
            "18: continue" -> "12: Dummy";
            "18: continue" -> "2: Termination";
            "19: Unreachable" -> "20: continue;";
            "20: continue;" -> "21: BLOCK";
            "17: cond" -> "22: IF";
            "21: BLOCK" -> "22: IF";
            "22: IF" -> "23: IF;";
            "23: IF;" -> "24: break";
            "24: break" -> "10: FOR";
            "24: break" -> "2: Termination";
            "25: Unreachable" -> "26: break;";
            "26: break;" -> "27: op3";
            "27: op3" -> "28: op3;";
            "28: op3;" -> "29: BLOCK";
            "29: BLOCK" -> "12: Dummy";
            "10: FOR" -> "30: BLOCK";
            "30: BLOCK" -> "5: Dummy";
            "3: FOR" -> "31: FOR;";
            "31: FOR;" -> "32: y";
            "32: y" -> "33: y;";
            "33: y;" -> "34: BLOCK";
            "34: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
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
        digraph {
            "0: Entry" -> "3: E::A";
            "3: E::A" -> "4: x";
            "4: x" -> "5: x";
            "5: x" -> "6: let x = E::A;";
            "6: let x = E::A;" -> "7: x";
            "7: x" -> "10: E::A";
            "10: E::A" -> "9: Dummy";
            "9: Dummy" -> "11: 1";
            "11: 1" -> "8: MATCH";
            "7: x" -> "13: x";
            "13: x" -> "14: x";
            "14: x" -> "15: E::B(x)";
            "15: E::B(x)" -> "12: Dummy";
            "12: Dummy" -> "16: x";
            "16: x" -> "19: 0...10";
            "19: 0...10" -> "18: Dummy";
            "18: Dummy" -> "20: 2";
            "20: 2" -> "17: MATCH";
            "16: x" -> "22: _";
            "22: _" -> "21: Dummy";
            "21: Dummy" -> "23: 3";
            "23: 3" -> "17: MATCH";
            "17: MATCH" -> "8: MATCH";
            "7: x" -> "25: E::C";
            "25: E::C" -> "24: Dummy";
            "24: Dummy" -> "26: 4";
            "26: 4" -> "8: MATCH";
            "8: MATCH" -> "27: MATCH;";
            "27: MATCH;" -> "28: 0";
            "28: 0" -> "29: y";
            "29: y" -> "30: y";
            "30: y" -> "31: let y = 0;";
            "31: let y = 0;" -> "32: BLOCK";
            "32: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
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
        digraph {
            "0: Entry" -> "3: E::A";
            "3: E::A" -> "4: 1";
            "4: 1" -> "5: E::A(1)";
            "5: E::A(1)" -> "6: x";
            "6: x" -> "7: x";
            "7: x" -> "8: let x = E::A(1);";
            "8: let x = E::A(1);" -> "9: x";
            "9: x" -> "12: val";
            "12: val" -> "13: val";
            "13: val" -> "14: E::A(val)";
            "14: E::A(val)" -> "15: Dummy";
            "15: Dummy" -> "16: val";
            "16: val" -> "17: 0";
            "17: 0" -> "18: val > 0";
            "18: val > 0" -> "19: if val > 0";
            "19: if val > 0" -> "11: Dummy";
            "11: Dummy" -> "20: val";
            "20: val" -> "10: MATCH";
            "9: x" -> "22: E::B";
            "22: E::B" -> "21: Dummy";
            "21: Dummy" -> "23: return";
            "23: return" -> "1: Exit";
            "24: Unreachable" -> "10: MATCH";
            "10: MATCH" -> "25: MATCH;";
            "25: MATCH;" -> "26: 0";
            "26: 0" -> "27: y";
            "27: y" -> "28: y";
            "28: y" -> "29: let y = 0;";
            "29: let y = 0;" -> "30: BLOCK";
            "30: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test match with missing arms`() = testCFG("""
        enum E { A(i32), B }

        fn main() {
            if let [] = match f() { } {
                return;
            } else {
                main();
            };
        }

        fn f() -> E { E::A(1) }
    """, """
        digraph {
            "0: Entry" -> "3: f";
            "3: f" -> "4: f()";
            "4: f()" -> "5: MATCH";
            "5: MATCH" -> "7: []";
            "7: []" -> "6: Dummy";
            "6: Dummy" -> "8: return";
            "8: return" -> "1: Exit";
            "9: Unreachable" -> "10: return;";
            "10: return;" -> "11: BLOCK";
            "5: MATCH" -> "12: main";
            "12: main" -> "13: main()";
            "13: main()" -> "14: main();";
            "14: main();" -> "15: BLOCK";
            "11: BLOCK" -> "16: IF";
            "15: BLOCK" -> "16: IF";
            "16: IF" -> "17: IF;";
            "17: IF;" -> "18: BLOCK";
            "18: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test match on never type`() = testCFG("""
        fn main() {
            g(true, match f() { }, 5u8);
        }

        fn f() -> ! { loop {} }
        fn g<A, B, C>(_ :A, _ :B, _ :C) {}
    """, """
        digraph {
            "0: Entry" -> "3: g";
            "3: g" -> "4: true";
            "4: true" -> "5: f";
            "5: f" -> "6: f()";
            "6: f()" -> "2: Termination";
            "7: Unreachable" -> "8: MATCH";
            "8: MATCH" -> "9: 5u8";
            "9: 5u8" -> "10: g(true, match f() { }, 5u8)";
            "10: g(true, match f() { }, 5u8)" -> "11: g(true, match f() { }, 5u8);";
            "11: g(true, match f() { }, 5u8);" -> "12: BLOCK";
            "12: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test match on infinite loop`() = testCFG("""
        fn main() {
            let x = match (loop {}) { };
            let y = 0;
        }
    """, """
        digraph {
            "0: Entry" -> "3: Dummy";
            "3: Dummy" -> "5: BLOCK";
            "5: BLOCK" -> "3: Dummy";
            "3: Dummy" -> "2: Termination";
            "4: LOOP" -> "6: (loop {})";
            "6: (loop {})" -> "7: MATCH";
            "7: MATCH" -> "8: x";
            "8: x" -> "9: x";
            "9: x" -> "10: let x = match (loop {}) { };";
            "10: let x = match (loop {}) { };" -> "11: 0";
            "11: 0" -> "12: y";
            "12: y" -> "13: y";
            "13: y" -> "14: let y = 0;";
            "14: let y = 0;" -> "15: BLOCK";
            "15: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test match on early exit`() = testCFG("""
        fn main() {
            let x = match { return; } { };
            let y = 0;
        }
    """, """
        digraph {
            "0: Entry" -> "4: return";
            "4: return" -> "1: Exit";
            "5: Unreachable" -> "6: return;";
            "6: return;" -> "7: BLOCK";
            "7: BLOCK" -> "3: BLOCK";
            "3: BLOCK" -> "8: MATCH";
            "8: MATCH" -> "9: x";
            "9: x" -> "10: x";
            "10: x" -> "11: let x = match { return; } { };";
            "11: let x = match { return; } { };" -> "12: 0";
            "12: 0" -> "13: y";
            "13: y" -> "14: y";
            "14: y" -> "15: let y = 0;";
            "15: let y = 0;" -> "16: BLOCK";
            "16: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test match on break block`() = testCFG("""
        fn main() {
            let x = 'b: { match break 'b true { } };
            let y = 0;
        }
    """, """
        digraph {
            "0: Entry" -> "4: true";
            "4: true" -> "5: break 'b true";
            "5: break 'b true" -> "3: BLOCK";
            "5: break 'b true" -> "2: Termination";
            "6: Unreachable" -> "7: MATCH";
            "7: MATCH" -> "8: MATCH;";
            "8: MATCH;" -> "9: true";
            "9: true" -> "10: break 'b true";
            "10: break 'b true" -> "3: BLOCK";
            "10: break 'b true" -> "2: Termination";
            "11: Unreachable" -> "12: MATCH";
            "12: MATCH" -> "3: BLOCK";
            "3: BLOCK" -> "13: x";
            "13: x" -> "14: x";
            "14: x" -> "15: let x = 'b: { match break 'b true { } };";
            "15: let x = 'b: { match break 'b true { } };" -> "16: 0";
            "16: 0" -> "17: y";
            "17: y" -> "18: y";
            "18: y" -> "19: let y = 0;";
            "19: let y = 0;" -> "20: BLOCK";
            "20: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test match on break loop`() = testCFG("""
        fn main() {
            let x = loop { match break true { } };
            let y = 0;
        }
    """, """
        digraph {
            "0: Entry" -> "3: Dummy";
            "3: Dummy" -> "5: true";
            "5: true" -> "6: break true";
            "6: break true" -> "4: LOOP";
            "6: break true" -> "2: Termination";
            "7: Unreachable" -> "8: MATCH";
            "8: MATCH" -> "9: BLOCK";
            "9: BLOCK" -> "3: Dummy";
            "3: Dummy" -> "2: Termination";
            "4: LOOP" -> "10: x";
            "10: x" -> "11: x";
            "11: x" -> "12: let x = loop { match break true { } };";
            "12: let x = loop { match break true { } };" -> "13: 0";
            "13: 0" -> "14: y";
            "14: y" -> "15: y";
            "15: y" -> "16: let y = 0;";
            "16: let y = 0;" -> "17: BLOCK";
            "17: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test try`() = testCFG("""
        fn main() {
            x.foo(a, b)?;
            y;
        }
    """, """
        digraph {
            "0: Entry" -> "4: x";
            "4: x" -> "5: a";
            "5: a" -> "6: b";
            "6: b" -> "7: x.foo(a, b)";
            "7: x.foo(a, b)" -> "8: Dummy";
            "8: Dummy" -> "1: Exit";
            "7: x.foo(a, b)" -> "3: x.foo(a, b)?";
            "3: x.foo(a, b)?" -> "9: x.foo(a, b)?;";
            "9: x.foo(a, b)?;" -> "10: y";
            "10: y" -> "11: y;";
            "11: y;" -> "12: BLOCK";
            "12: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test patterns`() = testCFG("""
        struct S { data1: i32, data2: i32 }

        fn main() {
            let x = S { data1: 42, data2: 24 };
            let S { data1: a, data2: b } = s;
            let S { data1, data2 } = x;
            let (x, (y, z)) = (1, (2, 3));
            [0, 1 + a];
        }
    """, """
        digraph {
            "0: Entry" -> "3: 42";
            "3: 42" -> "4: data1: 42";
            "4: data1: 42" -> "5: 24";
            "5: 24" -> "6: data2: 24";
            "6: data2: 24" -> "7: S { data1: 42, data2: 24 }";
            "7: S { data1: 42, data2: 24 }" -> "8: x";
            "8: x" -> "9: x";
            "9: x" -> "10: let x = S { data1: 42, data2: 24 };";
            "10: let x = S { data1: 42, data2: 24 };" -> "11: s";
            "11: s" -> "12: a";
            "12: a" -> "13: a";
            "13: a" -> "14: data1: a";
            "14: data1: a" -> "15: b";
            "15: b" -> "16: b";
            "16: b" -> "17: data2: b";
            "17: data2: b" -> "18: S { data1: a, data2: b }";
            "18: S { data1: a, data2: b }" -> "19: let S { data1: a, data2: b } = s;";
            "19: let S { data1: a, data2: b } = s;" -> "20: x";
            "20: x" -> "21: data1";
            "21: data1" -> "22: data1";
            "22: data1" -> "23: data2";
            "23: data2" -> "24: data2";
            "24: data2" -> "25: S { data1, data2 }";
            "25: S { data1, data2 }" -> "26: let S { data1, data2 } = x;";
            "26: let S { data1, data2 } = x;" -> "27: 1";
            "27: 1" -> "28: 2";
            "28: 2" -> "29: 3";
            "29: 3" -> "30: (2, 3)";
            "30: (2, 3)" -> "31: (1, (2, 3))";
            "31: (1, (2, 3))" -> "32: x";
            "32: x" -> "33: x";
            "33: x" -> "34: y";
            "34: y" -> "35: y";
            "35: y" -> "36: z";
            "36: z" -> "37: z";
            "37: z" -> "38: (y, z)";
            "38: (y, z)" -> "39: (x, (y, z))";
            "39: (x, (y, z))" -> "40: let (x, (y, z)) = (1, (2, 3));";
            "40: let (x, (y, z)) = (1, (2, 3));" -> "41: 0";
            "41: 0" -> "42: 1";
            "42: 1" -> "43: a";
            "43: a" -> "44: 1 + a";
            "44: 1 + a" -> "45: [0, 1 + a]";
            "45: [0, 1 + a]" -> "46: [0, 1 + a];";
            "46: [0, 1 + a];" -> "47: BLOCK";
            "47: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
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
        digraph {
            "0: Entry" -> "3: true";
            "3: true" -> "4: noreturn";
            "4: noreturn" -> "5: noreturn()";
            "5: noreturn()" -> "2: Termination";
            "6: Unreachable" -> "7: noreturn();";
            "7: noreturn();" -> "8: BLOCK";
            "3: true" -> "9: IF";
            "8: BLOCK" -> "9: IF";
            "9: IF" -> "10: IF;";
            "10: IF;" -> "11: 42";
            "11: 42" -> "12: 42;";
            "12: 42;" -> "13: BLOCK";
            "13: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
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
        digraph {
            "0: Entry" -> "3: true";
            "3: true" -> "4: foo";
            "4: foo" -> "5: 1";
            "5: 1" -> "6: noreturn";
            "6: noreturn" -> "7: noreturn()";
            "7: noreturn()" -> "2: Termination";
            "8: Unreachable" -> "9: foo.bar(1, noreturn())";
            "9: foo.bar(1, noreturn())" -> "10: foo.bar(1, noreturn());";
            "10: foo.bar(1, noreturn());" -> "11: BLOCK";
            "3: true" -> "12: IF";
            "11: BLOCK" -> "12: IF";
            "12: IF" -> "13: IF;";
            "13: IF;" -> "14: 42";
            "14: 42" -> "15: 42;";
            "15: 42;" -> "16: BLOCK";
            "16: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test panic macro call inside stmt`() = testCFG("""
        fn main() {
            1;
            if true { 2; } else { panic!(); }
            42;
        }
    """, """
        digraph {
            "0: Entry" -> "3: 1";
            "3: 1" -> "4: 1;";
            "4: 1;" -> "5: true";
            "5: true" -> "6: 2";
            "6: 2" -> "7: 2;";
            "7: 2;" -> "8: BLOCK";
            "5: true" -> "9: panic!()";
            "9: panic!()" -> "2: Termination";
            "10: Unreachable" -> "11: panic!();";
            "11: panic!();" -> "12: BLOCK";
            "8: BLOCK" -> "13: IF";
            "12: BLOCK" -> "13: IF";
            "13: IF" -> "14: IF;";
            "14: IF;" -> "15: 42";
            "15: 42" -> "16: 42;";
            "16: 42;" -> "17: BLOCK";
            "17: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test panic macro call outside stmt`() = testCFG("""
        fn main() {
            match x {
                true => 2,
                false => panic!()
            };
        }
    """, """
        digraph {
            "0: Entry" -> "3: x";
            "3: x" -> "6: true";
            "6: true" -> "5: Dummy";
            "5: Dummy" -> "7: 2";
            "7: 2" -> "4: MATCH";
            "3: x" -> "9: false";
            "9: false" -> "8: Dummy";
            "8: Dummy" -> "10: panic!()";
            "10: panic!()" -> "2: Termination";
            "11: Unreachable" -> "4: MATCH";
            "4: MATCH" -> "12: MATCH;";
            "12: MATCH;" -> "13: BLOCK";
            "13: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test macro call outside stmt`() = testCFG("""
        fn main() {
            match e {
                E::A => 2,
                E::B => some_macro!()
            };
        }
    """, """
        digraph {
            "0: Entry" -> "3: e";
            "3: e" -> "6: E::A";
            "6: E::A" -> "5: Dummy";
            "5: Dummy" -> "7: 2";
            "7: 2" -> "4: MATCH";
            "3: e" -> "9: E::B";
            "9: E::B" -> "8: Dummy";
            "8: Dummy" -> "10: some_macro!()";
            "10: some_macro!()" -> "4: MATCH";
            "4: MATCH" -> "11: MATCH;";
            "11: MATCH;" -> "12: BLOCK";
            "12: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test shorthand struct literal`() = testCFG("""
        struct S { x: i32 }

        fn foo(x: i32) {
            S { x };
        }
    """, """
        digraph {
            "0: Entry" -> "3: x";
            "3: x" -> "4: S { x }";
            "4: S { x }" -> "5: S { x };";
            "5: S { x };" -> "6: BLOCK";
            "6: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test struct literal dot dot syntax`() = testCFG("""
        struct S { x: i32, y: i32 }

        fn main() {
            S { x, ..s };
        }
    """, """
        digraph {
            "0: Entry" -> "3: x";
            "3: x" -> "4: s";
            "4: s" -> "5: S { x, ..s }";
            "5: S { x, ..s }" -> "6: S { x, ..s };";
            "6: S { x, ..s };" -> "7: BLOCK";
            "7: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test lambda expr`() = testCFG("""
        fn foo() {
            let f = |x: i32| { x + 1 };
        }
    """, """
        digraph {
            "0: Entry" -> "4: x";
            "4: x" -> "5: 1";
            "5: 1" -> "6: x + 1";
            "6: x + 1" -> "7: BLOCK";
            "7: BLOCK" -> "3: BLOCK";
            "3: BLOCK" -> "8: CLOSURE";
            "0: Entry" -> "8: CLOSURE";
            "8: CLOSURE" -> "9: f";
            "9: f" -> "10: f";
            "10: f" -> "11: let f = |x: i32| { x + 1 };";
            "11: let f = |x: i32| { x + 1 };" -> "12: BLOCK";
            "12: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test arbitrary macro call`() = testCFG("""
        macro_rules! my_macro {
            ($ e1:expr, $ e2:expr) => ($ e1 + $ e2);
        }

        fn main() {
            my_macro!(x, y);
        }
    """, """
        digraph {
            "0: Entry" -> "3: x";
            "3: x" -> "4: y";
            "4: y" -> "5: x + y";
            "5: x + y" -> "6: x + y;";
            "6: x + y;" -> "7: BLOCK";
            "7: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test println! macro call`() = testCFG("""
        fn main() {
            println!("{} {}", x, y);
        }
    """, """
        digraph {
            "0: Entry" -> "3: \"{} {}\"";
            "3: \"{} {}\"" -> "4: x";
            "4: x" -> "5: y";
            "5: y" -> "6: println!(\"{} {}\", x, y)";
            "6: println!(\"{} {}\", x, y)" -> "7: println!(\"{} {}\", x, y);";
            "7: println!(\"{} {}\", x, y);" -> "8: BLOCK";
            "8: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test vec! macro call`() = testCFG("""
        fn main() {
            vec![ S { x }, s1 ];
        }
    """, """
        digraph {
            "0: Entry" -> "3: x";
            "3: x" -> "4: S { x }";
            "4: S { x }" -> "5: s1";
            "5: s1" -> "6: vec![ S { x }, s1 ]";
            "6: vec![ S { x }, s1 ]" -> "7: vec![ S { x }, s1 ];";
            "7: vec![ S { x }, s1 ];" -> "8: BLOCK";
            "8: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test assert_eq! macro call`() = testCFG("""
        fn main() {
            assert_eq!(x, y);
        }
    """, """
        digraph {
            "0: Entry" -> "3: x";
            "3: x" -> "4: y";
            "4: y" -> "5: assert_eq!(x, y)";
            "5: assert_eq!(x, y)" -> "6: assert_eq!(x, y);";
            "6: assert_eq!(x, y);" -> "7: BLOCK";
            "7: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test panic in lambda expr`() = testCFG("""
        fn foo() {
            let f = || { panic!() };
            1;
        }
    """, """
        digraph {
            "0: Entry" -> "4: panic!()";
            "4: panic!()" -> "2: Termination";
            "5: Unreachable" -> "6: BLOCK";
            "6: BLOCK" -> "3: BLOCK";
            "3: BLOCK" -> "7: CLOSURE";
            "0: Entry" -> "7: CLOSURE";
            "7: CLOSURE" -> "8: f";
            "8: f" -> "9: f";
            "9: f" -> "10: let f = || { panic!() };";
            "10: let f = || { panic!() };" -> "11: 1";
            "11: 1" -> "12: 1;";
            "12: 1;" -> "13: BLOCK";
            "13: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    @ExpandMacros
    @CheckTestmarkHit(MacroExpansionManager.Testmarks.TooDeepExpansion::class)
    fun `test infinitely recursive macro call`() = testCFG("""
        macro_rules! infinite_macro {
            () => { infinite_macro!() };
        }
        fn foo() {
            1;
            infinite_macro!();
            2;
        }
    """, """
        digraph {
            "0: Entry" -> "3: 1";
            "3: 1" -> "4: 1;";
            "4: 1;" -> "5: infinite_macro ! ( )";
            "5: infinite_macro ! ( )" -> "6: infinite_macro ! ( )";
            "6: infinite_macro ! ( )" -> "7: infinite_macro ! ( )";
            "7: infinite_macro ! ( )" -> "8: infinite_macro ! ( )";
            "8: infinite_macro ! ( )" -> "9: infinite_macro ! ( )";
            "9: infinite_macro ! ( )" -> "10: infinite_macro ! ( )";
            "10: infinite_macro ! ( )" -> "11: infinite_macro ! ( )";
            "11: infinite_macro ! ( )" -> "12: infinite_macro ! ( )";
            "12: infinite_macro ! ( )" -> "13: infinite_macro ! ( )";
            "13: infinite_macro ! ( )" -> "14: infinite_macro ! ( )";
            "14: infinite_macro ! ( )" -> "15: infinite_macro ! ( )";
            "15: infinite_macro ! ( )" -> "16: infinite_macro ! ( )";
            "16: infinite_macro ! ( )" -> "17: infinite_macro ! ( )";
            "17: infinite_macro ! ( )" -> "18: infinite_macro ! ( )";
            "18: infinite_macro ! ( )" -> "19: infinite_macro ! ( )";
            "19: infinite_macro ! ( )" -> "20: infinite_macro ! ( )";
            "20: infinite_macro ! ( )" -> "21: infinite_macro ! ( )";
            "21: infinite_macro ! ( )" -> "22: infinite_macro ! ( )";
            "22: infinite_macro ! ( )" -> "23: infinite_macro ! ( )";
            "23: infinite_macro ! ( )" -> "24: infinite_macro ! ( )";
            "24: infinite_macro ! ( )" -> "25: infinite_macro ! ( )";
            "25: infinite_macro ! ( )" -> "26: infinite_macro ! ( )";
            "26: infinite_macro ! ( )" -> "27: infinite_macro ! ( )";
            "27: infinite_macro ! ( )" -> "28: infinite_macro ! ( )";
            "28: infinite_macro ! ( )" -> "29: infinite_macro ! ( )";
            "29: infinite_macro ! ( )" -> "30: infinite_macro ! ( )";
            "30: infinite_macro ! ( )" -> "31: infinite_macro ! ( )";
            "31: infinite_macro ! ( )" -> "32: infinite_macro ! ( )";
            "32: infinite_macro ! ( )" -> "33: infinite_macro ! ( )";
            "33: infinite_macro ! ( )" -> "34: infinite_macro ! ( )";
            "34: infinite_macro ! ( )" -> "35: infinite_macro ! ( )";
            "35: infinite_macro ! ( )" -> "36: infinite_macro ! ( )";
            "36: infinite_macro ! ( )" -> "37: infinite_macro ! ( )";
            "37: infinite_macro ! ( )" -> "38: infinite_macro ! ( )";
            "38: infinite_macro ! ( )" -> "39: infinite_macro ! ( )";
            "39: infinite_macro ! ( )" -> "40: infinite_macro ! ( )";
            "40: infinite_macro ! ( )" -> "41: infinite_macro ! ( )";
            "41: infinite_macro ! ( )" -> "42: infinite_macro ! ( )";
            "42: infinite_macro ! ( )" -> "43: infinite_macro ! ( )";
            "43: infinite_macro ! ( )" -> "44: infinite_macro ! ( )";
            "44: infinite_macro ! ( )" -> "45: infinite_macro ! ( )";
            "45: infinite_macro ! ( )" -> "46: infinite_macro ! ( )";
            "46: infinite_macro ! ( )" -> "47: infinite_macro ! ( )";
            "47: infinite_macro ! ( )" -> "48: infinite_macro ! ( )";
            "48: infinite_macro ! ( )" -> "49: infinite_macro ! ( )";
            "49: infinite_macro ! ( )" -> "50: infinite_macro ! ( )";
            "50: infinite_macro ! ( )" -> "51: infinite_macro ! ( )";
            "51: infinite_macro ! ( )" -> "52: infinite_macro ! ( )";
            "52: infinite_macro ! ( )" -> "53: infinite_macro ! ( )";
            "53: infinite_macro ! ( )" -> "54: infinite_macro ! ( )";
            "54: infinite_macro ! ( )" -> "55: infinite_macro ! ( )";
            "55: infinite_macro ! ( )" -> "56: infinite_macro ! ( )";
            "56: infinite_macro ! ( )" -> "57: infinite_macro ! ( )";
            "57: infinite_macro ! ( )" -> "58: infinite_macro ! ( )";
            "58: infinite_macro ! ( )" -> "59: infinite_macro ! ( )";
            "59: infinite_macro ! ( )" -> "60: infinite_macro ! ( )";
            "60: infinite_macro ! ( )" -> "61: infinite_macro ! ( )";
            "61: infinite_macro ! ( )" -> "62: infinite_macro ! ( )";
            "62: infinite_macro ! ( )" -> "63: infinite_macro ! ( )";
            "63: infinite_macro ! ( )" -> "64: infinite_macro ! ( )";
            "64: infinite_macro ! ( )" -> "65: infinite_macro ! ( )";
            "65: infinite_macro ! ( )" -> "66: infinite_macro ! ( )";
            "66: infinite_macro ! ( )" -> "67: infinite_macro ! ( )";
            "67: infinite_macro ! ( )" -> "68: infinite_macro ! ( )";
            "68: infinite_macro ! ( )" -> "69: infinite_macro ! ( )";
            "69: infinite_macro ! ( )" -> "70: infinite_macro ! ( )";
            "70: infinite_macro ! ( )" -> "71: infinite_macro ! ( )";
            "71: infinite_macro ! ( )" -> "72: infinite_macro ! ( )";
            "72: infinite_macro ! ( )" -> "73: infinite_macro ! ( )";
            "73: infinite_macro ! ( )" -> "74: infinite_macro ! ( )";
            "74: infinite_macro ! ( )" -> "75: infinite_macro ! ( )";
            "75: infinite_macro ! ( )" -> "76: infinite_macro ! ( )";
            "76: infinite_macro ! ( )" -> "77: infinite_macro ! ( )";
            "77: infinite_macro ! ( )" -> "78: infinite_macro ! ( )";
            "78: infinite_macro ! ( )" -> "79: infinite_macro ! ( )";
            "79: infinite_macro ! ( )" -> "80: infinite_macro ! ( )";
            "80: infinite_macro ! ( )" -> "81: infinite_macro ! ( )";
            "81: infinite_macro ! ( )" -> "82: infinite_macro ! ( )";
            "82: infinite_macro ! ( )" -> "83: infinite_macro ! ( )";
            "83: infinite_macro ! ( )" -> "84: infinite_macro ! ( )";
            "84: infinite_macro ! ( )" -> "85: infinite_macro ! ( )";
            "85: infinite_macro ! ( )" -> "86: infinite_macro ! ( )";
            "86: infinite_macro ! ( )" -> "87: infinite_macro ! ( )";
            "87: infinite_macro ! ( )" -> "88: infinite_macro ! ( )";
            "88: infinite_macro ! ( )" -> "89: infinite_macro ! ( )";
            "89: infinite_macro ! ( )" -> "90: infinite_macro ! ( )";
            "90: infinite_macro ! ( )" -> "91: infinite_macro ! ( )";
            "91: infinite_macro ! ( )" -> "92: infinite_macro ! ( )";
            "92: infinite_macro ! ( )" -> "93: infinite_macro ! ( )";
            "93: infinite_macro ! ( )" -> "94: infinite_macro ! ( )";
            "94: infinite_macro ! ( )" -> "95: infinite_macro ! ( )";
            "95: infinite_macro ! ( )" -> "96: infinite_macro ! ( )";
            "96: infinite_macro ! ( )" -> "97: infinite_macro ! ( )";
            "97: infinite_macro ! ( )" -> "98: infinite_macro ! ( )";
            "98: infinite_macro ! ( )" -> "99: infinite_macro ! ( )";
            "99: infinite_macro ! ( )" -> "100: infinite_macro ! ( )";
            "100: infinite_macro ! ( )" -> "101: infinite_macro ! ( )";
            "101: infinite_macro ! ( )" -> "102: infinite_macro ! ( )";
            "102: infinite_macro ! ( )" -> "103: infinite_macro ! ( )";
            "103: infinite_macro ! ( )" -> "104: infinite_macro ! ( )";
            "104: infinite_macro ! ( )" -> "105: infinite_macro ! ( )";
            "105: infinite_macro ! ( )" -> "106: infinite_macro ! ( )";
            "106: infinite_macro ! ( )" -> "107: infinite_macro ! ( )";
            "107: infinite_macro ! ( )" -> "108: infinite_macro ! ( )";
            "108: infinite_macro ! ( )" -> "109: infinite_macro ! ( )";
            "109: infinite_macro ! ( )" -> "110: infinite_macro ! ( )";
            "110: infinite_macro ! ( )" -> "111: infinite_macro ! ( )";
            "111: infinite_macro ! ( )" -> "112: infinite_macro ! ( )";
            "112: infinite_macro ! ( )" -> "113: infinite_macro ! ( )";
            "113: infinite_macro ! ( )" -> "114: infinite_macro ! ( )";
            "114: infinite_macro ! ( )" -> "115: infinite_macro ! ( )";
            "115: infinite_macro ! ( )" -> "116: infinite_macro ! ( )";
            "116: infinite_macro ! ( )" -> "117: infinite_macro ! ( )";
            "117: infinite_macro ! ( )" -> "118: infinite_macro ! ( )";
            "118: infinite_macro ! ( )" -> "119: infinite_macro ! ( )";
            "119: infinite_macro ! ( )" -> "120: infinite_macro ! ( )";
            "120: infinite_macro ! ( )" -> "121: infinite_macro ! ( )";
            "121: infinite_macro ! ( )" -> "122: infinite_macro ! ( )";
            "122: infinite_macro ! ( )" -> "123: infinite_macro ! ( )";
            "123: infinite_macro ! ( )" -> "124: infinite_macro ! ( )";
            "124: infinite_macro ! ( )" -> "125: infinite_macro ! ( )";
            "125: infinite_macro ! ( )" -> "126: infinite_macro ! ( )";
            "126: infinite_macro ! ( )" -> "127: infinite_macro ! ( )";
            "127: infinite_macro ! ( )" -> "128: infinite_macro ! ( )";
            "128: infinite_macro ! ( )" -> "129: infinite_macro ! ( )";
            "129: infinite_macro ! ( )" -> "130: infinite_macro ! ( )";
            "130: infinite_macro ! ( )" -> "131: infinite_macro ! ( )";
            "131: infinite_macro ! ( )" -> "132: infinite_macro ! ( )";
            "132: infinite_macro ! ( )" -> "133: infinite_macro ! ( );";
            "133: infinite_macro ! ( );" -> "134: 2";
            "134: 2" -> "135: 2;";
            "135: 2;" -> "136: BLOCK";
            "136: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test async block with infinite loop`() = testCFG("""
        fn foo() {
            1;
            async { loop {} };
            2;
        }
    """, """
        digraph {
            "0: Entry" -> "3: 1";
            "3: 1" -> "4: 1;";
            "4: 1;" -> "6: Dummy";
            "6: Dummy" -> "8: BLOCK";
            "8: BLOCK" -> "6: Dummy";
            "6: Dummy" -> "2: Termination";
            "7: LOOP" -> "9: BLOCK";
            "9: BLOCK" -> "5: BLOCK";
            "4: 1;" -> "5: BLOCK";
            "5: BLOCK" -> "10: BLOCK;";
            "10: BLOCK;" -> "11: 2";
            "11: 2" -> "12: 2;";
            "12: 2;" -> "13: BLOCK";
            "13: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test loop with break inside block expr`() = testCFG("""
        fn main() {
            loop {
                {
                    break;
                }
            }
            1;
        }
    """, """
        digraph {
            "0: Entry" -> "3: Dummy";
            "3: Dummy" -> "6: break";
            "6: break" -> "4: LOOP";
            "6: break" -> "2: Termination";
            "7: Unreachable" -> "8: break;";
            "8: break;" -> "9: BLOCK";
            "9: BLOCK" -> "5: BLOCK";
            "5: BLOCK" -> "10: BLOCK";
            "10: BLOCK" -> "3: Dummy";
            "3: Dummy" -> "2: Termination";
            "4: LOOP" -> "11: LOOP;";
            "11: LOOP;" -> "12: 1";
            "12: 1" -> "13: 1;";
            "13: 1;" -> "14: BLOCK";
            "14: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    @MinRustcVersion("1.62.0")
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test conditional code`() = testCFG("""
        macro_rules! reachable { () => {}; }
        macro_rules! unreachable { () => { foo; }; }
        fn main() {
            1;
            #[cfg(intellij_rust)] 2;
            #[cfg(not(intellij_rust))] unreachable;
            #[cfg(not(intellij_rust))] return;
            #[cfg(not(intellij_rust))] let x = unreachable();
            let s = S { #[cfg(not(intellij_rust))] x: unreachable() };
            foo(#[cfg(not(intellij_rust))] unreachable);
            #[cfg(intellij_rust)] reachable!();
            #[cfg(not(intellij_rust))] unreachable!();
            #[cfg(not(intellij_rust))] panic!();
            4;
            #[cfg(intellij_rust)] panic!();
            5;
        }
    """, """
        digraph {
            "0: Entry" -> "3: 1";
            "3: 1" -> "4: 1;";
            "4: 1;" -> "5: 2";
            "5: 2" -> "6: #[cfg(intellij_rust)] 2;";
            "6: #[cfg(intellij_rust)] 2;" -> "7: S { #[cfg(not(intellij_rust))] x: unreachable() }";
            "7: S { #[cfg(not(intellij_rust))] x: unreachable() }" -> "8: s";
            "8: s" -> "9: s";
            "9: s" -> "10: let s = S { #[cfg(not(intellij_rust))] x: unreachable() };";
            "10: let s = S { #[cfg(not(intellij_rust))] x: unreachable() };" -> "11: foo";
            "11: foo" -> "12: foo(#[cfg(not(intellij_rust))] unreachable)";
            "12: foo(#[cfg(not(intellij_rust))] unreachable)" -> "13: foo(#[cfg(not(intellij_rust))] unreachable);";
            "13: foo(#[cfg(not(intellij_rust))] unreachable);" -> "14: 4";
            "14: 4" -> "15: 4;";
            "15: 4;" -> "16: panic!()";
            "16: panic!()" -> "2: Termination";
            "17: Unreachable" -> "18: #[cfg(intellij_rust)] panic!();";
            "18: #[cfg(intellij_rust)] panic!();" -> "19: 5";
            "19: 5" -> "20: 5;";
            "20: 5;" -> "21: BLOCK";
            "21: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test break expanded from macro`() = testCFG("""
        macro_rules! break_macro {
            () => { break };
        }
        fn main() {
            1;
            loop {
                break_macro!();
            }
            2;
        }
    """, """
        digraph {
            "0: Entry" -> "3: 1";
            "3: 1" -> "4: 1;";
            "4: 1;" -> "5: Dummy";
            "5: Dummy" -> "7: break";
            "7: break" -> "6: LOOP";
            "7: break" -> "2: Termination";
            "8: Unreachable" -> "9: break;";
            "9: break;" -> "10: BLOCK";
            "10: BLOCK" -> "5: Dummy";
            "5: Dummy" -> "2: Termination";
            "6: LOOP" -> "11: LOOP;";
            "11: LOOP;" -> "12: 2";
            "12: 2" -> "13: 2;";
            "13: 2;" -> "14: BLOCK";
            "14: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    fun `test asm! macro call`() = testCFG("""
        fn main() {
            1;
            let x: u64;
            unsafe {
                asm!("mov {}, 5", out(reg) x);
            }
            2;
        }
    """, """
        digraph {
            "0: Entry" -> "3: 1";
            "3: 1" -> "4: 1;";
            "4: 1;" -> "5: x";
            "5: x" -> "6: x";
            "6: x" -> "7: let x: u64;";
            "7: let x: u64;" -> "9: x";
            "9: x" -> "10: asm!(\"mov {}, 5\", out(reg) x)";
            "10: asm!(\"mov {}, 5\", out(reg) x)" -> "11: asm!(\"mov {}, 5\", out(reg) x);";
            "11: asm!(\"mov {}, 5\", out(reg) x);" -> "12: BLOCK";
            "12: BLOCK" -> "8: BLOCK";
            "8: BLOCK" -> "13: BLOCK;";
            "13: BLOCK;" -> "14: 2";
            "14: 2" -> "15: 2;";
            "15: 2;" -> "16: BLOCK";
            "16: BLOCK" -> "1: Exit";
            "1: Exit" -> "2: Termination";
        }
    """)

    private fun testCFG(@Language("Rust") code: String, @Language("Dot") expectedIndented: String) {
        InlineFile(code)
        val function = myFixture.file.descendantsOfType<RsFunction>().firstOrNull() ?: return
        val cfg = ControlFlowGraph.buildFor(function.block!!, getRegionScopeTree(function))
        val expected = expectedIndented.trimIndent()
        val actual = cfg.graph.createDotDescription().trimEnd()
        assertEquals(expected, actual)
    }
}
