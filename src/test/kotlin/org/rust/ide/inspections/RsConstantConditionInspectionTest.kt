/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.intellij.lang.annotations.Language

class RsConstantConditionInspectionTest : RsInspectionsTestBase(RsConstantConditionInspection()) {
    fun `test declaration from integer constant`() = checkDeclaration("42", "{42}")

    fun `test declaration from integer constant with suffix`() = checkDeclaration("42u16", "{42}")

    fun `test declaration from integer constant with separator`() = checkDeclaration("1_000__000", "{1000000}")

    fun `test declaration from unary expression`() = checkDeclaration("-42", "{-42}")

    fun `test declaration from binary expression`() = checkDeclaration("21 + 21", "{42}")

    fun `test declaration from boolean constant`() = checkDeclaration("true", "{true}")

    private fun checkDeclaration(expression: String, value: String) = checkWithExpandValues("""
        fn main() {
            let x/*$value*/: i32 = $expression;
        }
    """)

    fun `test declaration from overflow expression`() = checkWithExpandValues("""
        fn main() {
            let x/*{200}*/ = 200u8;
            let y= <warning descr="Expression `x * 1u8 * 2u8` is overflow">x * 1u8 * 2u8</warning>;
        }
    """)

    fun `test declaration from overflow literal expression`() = checkWithExpandValues("""
        fn main() {
            let x = <warning descr="Literal out of range for i8">200i8</warning>;
        }
    """)

    fun `test division by zero stops the analysis 1`() = checkWithExpandValues("""
        fn main() {
            let x/*{0}*/ = 5 * 0;
            let a = <error descr="Division by zero">10 % x</error>;
            let f = true;
            if f {

            }
        }
    """)

    fun `test division by zero stops the analysis 2`() = checkWithExpandValues("""
        fn main() {
            let x/*{0}*/ = 5 * 0;
            let a = <error descr="Division by zero">10 / x</error>;
            let f = true;
            if f {

            }
        }
    """)

    fun `test declaration from arguments`() = checkWithExpandValues("""
        fn foo(
            a: i32,
            b: bool,
            c: u8,
            d: i128
            ) {
                let a/*{-2147483648..2147483647}*/ = a;
                let c/*u8{0..255}*/ = c;
                let d/*i128{-9223372036854775808..9223372036854775807}*/ = d;
            }
    """)

    fun `test declaration from tuple of 2 elements`() = checkWithExpandValues("""
        fn main() {
            let (x/*{42}*/, y/*{24}*/) = (42, 24);
            let x/*{42}*/ = x;
            let y/*{24}*/ = y;
        }
    """)

    fun `test declaration from tuple of 3 elements`() = checkWithExpandValues("""
        fn main() {
            let (x, y, z) = (1, true , -3);
            let x/*{1}*/ = x;
            let y/*{true}*/ = y;
            let z/*{-3}*/ = z;
        }
    """)

    fun `test declaration from function`() = checkWithExpandValues("""
        fn foo() -> i8 { 42 }
        fn main() {
            let x/*i8{-128..127}*/ = foo();
        }
    """)

    fun `test declaration equal to myself`() = checkWithExpandValues("""
        fn foo(a: bool) {
            let t/*{true}*/ = a == a;
            let f/*{false}*/ = a != a;
        }
    """)

    fun `test declaration equal to other with unknown`() = checkWithExpandValues("""
        fn foo(a: bool, b: bool) {
            let x = a == b;
            let y = a != b;
        }
    """)

    fun `test declaration equal to other with constant`() = checkWithExpandValues("""
        fn foo(a: bool) {
            let b/*{true}*/ = true;
            let x = a == b;
            let y = a != b;
        }
    """)

    fun `test declaration with identical names`() = checkWithExpandValues("""
        fn foo(a: bool) {
            let t/*{true}*/ = a == a;
            let f/*{false}*/ = a != a;
        }
    """)

    fun `test overflow in compare`() = checkWithExpandValues("""
        fn test(input: i32) {
            if input > 2000000000 && <warning descr="Expression `input * 10` is overflow">input * 10</warning> > 2000000001 {

            }
        }
    """)

    fun `test if with literal expression`() = checkWithExpandValues("""
       fn main() {
            if <warning descr="Condition is always `true`">true</warning> {
                // do smth
            }

            if <warning descr="Condition is always `false`">false</warning> {
                // do smth
            }
       }
    """)

    fun `test while with literal expression`() = checkWithExpandValues("""
       fn main() {
            while <warning descr="Condition is always `false`">false</warning> {
                // do smth
            }
       }
    """)

    fun `test while with always true expression`() = checkWithExpandValues("""
       fn main() {
            let a = true;
            while <warning descr="Condition `a == true` is always `true`">a == true</warning> {
                if <warning descr="Condition `a == true` is always `true`">a == true</warning>  {}
                // do smth
            }

            // mustn't analyze
            if a == true  {}
       }
    """)

    fun `test simple boolean expression with or`() = checkWithExpandValues("""
       fn main() {
            let a/*{true}*/ = true;
            let b/*{false}*/ = false;
            if <warning descr="Condition `a || b` is always `true`">a || b</warning> {
                let c/*{true}*/ = a;
                let d/*{false}*/ = b;
            } else {
                let c = b;
                let d = a;
            }
       }
    """)

    fun `test simple boolean expression with and`() = checkWithExpandValues("""
       fn main() {
            let a/*{true}*/ = true;
            let b/*{false}*/ = false;
            if <warning descr="Condition `a && b` is always `false`">a && b</warning> {
                let c = a;
                let d = b;
            } else {
                let c/*{false}*/ = b;
                let d/*{true}*/ = a;
            }
       }
    """)

    fun `test several constant condition`() = checkWithExpandValues("""
       fn foo(a: bool) {
            let b/*{false}*/ = false;
            let c/*{true}*/ = true;
            if a || <warning descr="Condition `c && b` is always `false`">c && b</warning> || <warning descr="Condition `b && c` is always `false`">b && c</warning> {

            }
       }
    """)

    fun `test apply condition 1`() = checkWithExpandValues("""
       fn foo(b: i8) {
            let x/*i8{42}*/: i8 = 42i8;
            let a: i8;
            if b > 10i8 && b != x {
                a = x + 5i8;
                let b = b;
                if <warning descr="Condition `11i8 <= b` is always `true`">11i8 <= b</warning> && b <= 41i8 || 43i8 <= b && <warning descr="Condition `b <= 127i8` is always `true`">b <= 127i8</warning> {}
            } else {
                a = x - 5i8;
                let b/*i8{-128..127}*/ = b;
            }
            let a/*i8{37, 47}*/ = a;
       }
    """)

    fun `test apply condition 2`() = checkWithExpandValues("""
       fn foo(a: bool) {
            let a = a;
            let x/*{true}*/ = true;
            if a != x {
                let c/*{true}*/ = x;
                let d/*{false}*/ = a;
            } else {
                let c/*{true}*/ = x;
                let d/*{true}*/ = a;
            }
       }
    """)

    fun `test apply condition 3`() = checkWithExpandValues("""
       fn test(input: i32) {
            let x/*{42}*/ = 42;
            let a: i32;
            if input > 50 {
                a = input + 10;
                if <warning descr="Condition `a < 60` is always `false`">a < 60</warning> {

                }
            } else {
                a = input - 10;
                let a/*{-2147483648..40}*/ = a;
            }
            let a/*{-2147483648..40, 61..2147483647}*/ = a;
        }
    """)

    fun `test apply condition 4`() = checkWithExpandValues("""
       fn test(input: i32) {
            if input >= 50 {
                if input < 100 {
                    let a1/*{60..109}*/ = input + 10;
                    let b1/*{40..89}*/ = input - 10;
                    let c1/*{500..990}*/ = input * 10;
                    let d1/*{0..9}*/ = input % 10;
                    let e1/*{5..9}*/ = input / 10;
                } else {
                    let a2/*{110..2147483647}*/ = input + 10;
                    let b2/*{90..2147483637}*/ = input - 10;
                    let c2/*{1000..2147483647}*/ = input * 10;
                    let d2/*{0..9}*/ = input % 10;
                    let e2/*{10..214748364}*/ = input / 10;
                }
            } else {
                    let a3/*{-2147483638..59}*/ = input + 10;
                    let b3/*{-2147483648..39}*/ = input - 10;
                    let c3 = input * 10;
                    if <warning descr="Condition `-2147483648 <= c3` is always `true`">-2147483648 <= c3</warning> && c3 <= -10 || c3 == 0 || 10 <= c3 && <warning descr="Condition `c3 <= 490` is always `true`">c3 <= 490</warning> {}
                    let d3/*{-9..9}*/ = input % 10;
                    let e3/*{-214748364..4}*/ = input / 10;
            }
        }
    """)

    fun `test apply condition 5`() = checkWithExpandValues("""
       fn test(input: i32) {
            let x/*{12}*/ = 12;
            let y/*{77}*/ = 77;
            let a: i32;
            if input < 42 {
                a = y - 16;
                let f: i32;
                if <warning descr="Condition `a > 50` is always `true`">a > 50</warning> {
                    f = input % 100;
                } else {
                    f = 500;
                }
                let f/*{-99..41}*/ = f;
                let z = input * 10;
                if <warning descr="Condition `-2147483648 <= z` is always `true`">-2147483648 <= z</warning> && z <= -10 || z == 0 || 10 <= z && <warning descr="Condition `z <= 410` is always `true`">z <= 410</warning> {}
            } else {
                a = x + 4;
                let a/*{16}*/ = a;
            }
            let a/*{16, 61}*/ = a;

            if <warning descr="Condition `a == 13` is always `false`">a == 13</warning> {
                println!("{}", a);
            }
        }
    """)

    fun `test it let`() = checkWithExpandValues("""
       fn foo(input: i32) {
            if let 32 = input {
                let c/*{1000}*/ = 1000;
            } else {
                let c/*{500}*/ = 500;
            }

            let a/*{10}*/ = 10;
       }
    """)

    fun `test with unary operator`() = checkWithExpandValues("""
       fn test(input: i32) {
            if !(input == 42 || input > 50) && input >= 0 {
                let a = input;
                if <warning descr="Condition `0 <= a` is always `true`">0 <= a</warning> && a <= 41 || 43 <= a && <warning descr="Condition `a <= 50` is always `true`">a <= 50</warning> {}
            } else {
                let a/*{-2147483648..2147483647}*/ = input;
            }
            let a/*{-2147483648..2147483647}*/ = input;
        }
    """)

    fun `test with unary operator always false`() = checkWithExpandValues("""
       fn test(input: i32) {
            if <warning descr="Condition `!(input != 42 || input != 50)` is always `false`">!(input != 42 || input != 50)</warning> {
                let a = input;
            } else {
                let a/*{-2147483648..2147483647}*/ = input;
            }
            let a/*{-2147483648..2147483647}*/ = input;
        }
    """)

    fun `test with unary operator always true`() = checkWithExpandValues("""
       fn test(input: i32) {
            if <warning descr="Condition `!(input == 42 && input == 50)` is always `true`">!(input == 42 && input == 50)</warning> {
                let a/*{-2147483648..2147483647}*/ = input;
            } else {
                let a = input;
            }
            let a/*{-2147483648..2147483647}*/ = input;
        }
    """)

    fun `test simple match`() = checkWithExpandValues("""
       fn test(input: i32) {
            let input/*{-2147483648..2147483647}*/ = input;
            let a: i32;
            match input {
                1 => { a = 1 }
                5 => { a = -10 }
                _ => { a = 50 }
            }
            let b/*{-10, 1, 50}*/ = a;
            let c/*i8{-128}*/ = -(128i8);
        }
    """)

    fun `test simple coherence`() = checkWithExpandValues("""
       fn test(input: i32) {
            let x: i32;
            let y: i32;
            if input == 50 {
              x = 1;
              y = 1;
            } else {
              x = 2;
              y = 2;
            }
            if x == 1 {
               if <warning descr="Condition `y == 1` is always `true`">y == 1</warning> {}
            }
        }
    """)

    fun `test simple coherence 2`() = checkWithExpandValues("""
       fn test(input: i32) {
            let x: i32;
            let y: i32;
            if input == 50 {
              x = 1;
              y = 1;
            } else {
              x = 2;
              y = 2;
            }
            if input != 50 {
               if <warning descr="Condition `y == 2 && x == 2` is always `true`">y == 2 && x == 2</warning> {}
            }
        }
    """)

    fun `test too many branches`() = checkByText("""
       <warning descr="Couldn't analyze function TOO_COMPLEX: Too complex data flow: too many branches processed">fn test</warning>(input: i32) {
            ${ifBomb(10)}// 2^deep
        }
    """)

    fun `test too many states`() = checkByText("""
       <warning descr="Couldn't analyze function TOO_COMPLEX: Too complex data flow: too many instruction states processed">fn test</warning>(input: i32) {
            ${ifBomb(5, declareVariables(150))}
        }
    """)

    private fun ifBomb(deep: Int, prefix: String = ""): String = if (deep <= 0) ""
    else {
        val inner = ifBomb(deep - 1, prefix)
        """
        $prefix
        let a: bool = foo();
        if a { $inner } else { $inner }
    """
    }

    private fun declareVariables(count: Int): String = buildString {
        for (i in 0..count) {
            append("let _a")
            append(i)
            append(" = 1;")
            append('\n')
        }
    }

    private fun checkWithExpandValues(@Language("Rust") text: String) = checkByText(text.expandValues)

    private val String.expandValues: String
        get() = setOfValuesRegex.replace(this) {
            val variableName = it.value.substringBefore('/')
            val type = it.value.substringAfter("/*").substringBefore('{')
            val setOfValue = it.value.substringAfter("{").substringBefore("}*/")
            val condition = setOfValue.splitToSequence(',').map { string ->
                val numbers = numberRegex.findAll(string).map { it.value.toLong() }.toList()
                when (numbers.size) {
                    1 -> "$variableName == ${numbers[0]}$type"
                    2 -> "${numbers[0]}$type <= $variableName && $variableName <= ${numbers[1]}$type"
                    else -> "$variableName == $string"
                }
            }.joinToString(separator = " || ")
            "${it.value}\nif <warning descr=\"Condition `$condition` is always `true`\">$condition</warning> {}"
        }

    companion object {
        private val setOfValuesRegex = Regex("([_a-zA-Z0-9])*(/[*]).*?([*]/).*")
        private val numberRegex = Regex("-?[\\d]+")
    }
}
