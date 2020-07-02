/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsLiftInspectionTest : RsInspectionsTestBase(RsLiftInspection::class) {

    fun `test lift return in if 1`() = checkFixByText("Lift return out of 'if'", """
        fn foo(x: bool) -> i32 {
            /*weak_warning descr="Return can be lifted out of 'if'"*/if/*caret*//*weak_warning**/ x {
                println("Hello!");
                return 1
            } else {
                return 0
            }
        }
    """, """
        fn foo(x: bool) -> i32 {
            return if x {
                println("Hello!");
                1
            } else {
                0
            }
        }
    """, checkWeakWarn = true)

    fun `test lift return in if 2`() = checkFixByText("Lift return out of 'if'", """
        fn foo(x: bool) -> i32 {
            /*weak_warning descr="Return can be lifted out of 'if'"*/if/*caret*//*weak_warning**/ x {
                return 1;
            } else {
                return 0;
            };
        }
    """, """
        fn foo(x: bool) -> i32 {
            return if x {
                1
            } else {
                0
            };
        }
    """, checkWeakWarn = true)

    fun `test lift return in if unavailable`() = checkFixIsUnavailable("Lift return out of 'if'", """
        fn foo(x: bool) -> i32 {
            if/*caret*/ x {
                1
            } else {
                return 0;
            };
            return 1;
        }
    """, checkWeakWarn = true)

    fun `test lift return in if with return`() = checkFixByText("Lift return out of 'if'", """
        fn foo(x: bool) -> i32 {
            return /*weak_warning descr="Return can be lifted out of 'if'"*/if/*caret*//*weak_warning**/ x {
                return 1;
            } else {
                return 0;
            };
        }
    """, """
        fn foo(x: bool) -> i32 {
            return if x {
                1
            } else {
                0
            };
        }
    """, checkWeakWarn = true, testmark = RsLiftInspection.Testmarks.insideRetExpr)

    fun `test lift return in else if`() = checkFixIsUnavailable("Lift return out of 'if'", """
        fn get_char(n: u32) -> char {
            /*weak_warning descr="Return can be lifted out of 'if'"*/if/*weak_warning**/ n == 0 {
                return 'a'
            } else /*caret*/if n == 1 {
                return 'b'
            } else {
                return 'c'
            }
        }
    """, checkWeakWarn = true)

    fun `test lift return in if with else if`() = checkFixByText("Lift return out of 'if'", """
        fn get_char(n: u32) -> char {
            /*weak_warning descr="Return can be lifted out of 'if'"*/if/*caret*//*weak_warning**/ n == 0 {
                return 'a'
            } else if n == 1 {
                return 'b'
            } else if n == 2 {
                return 'c'
            } else {
                return 'd'
            }
        }
    """, """
        fn get_char(n: u32) -> char {
            return if n == 0 {
                'a'
            } else if n == 1 {
                'b'
            } else if n == 2 {
                'c'
            } else {
                'd'
            }
        }
    """, checkWeakWarn = true)

    fun `test lift return in match 1`() = checkFixByText("Lift return out of 'match'", """
        fn foo(x: bool) -> i32 {
            /*weak_warning descr="Return can be lifted out of 'match'"*/match/*caret*//*weak_warning**/ x {
                true => return 1,
                false => return 0
            }
        }
    """, """
        fn foo(x: bool) -> i32 {
            return match x {
                true => 1,
                false => 0
            }
        }
    """, checkWeakWarn = true)

    fun `test lift return in match 2`() = checkFixByText("Lift return out of 'match'", """
        fn foo(x: bool) -> i32 {
            /*weak_warning descr="Return can be lifted out of 'match'"*/match/*caret*//*weak_warning**/ x {
                true => {
                    return 1
                },
                false => {
                    return 0;
                }
            }
        }
    """, """
        fn foo(x: bool) -> i32 {
            return match x {
                true => {
                    1
                },
                false => {
                    0
                }
            }
        }
    """, checkWeakWarn = true)

    fun `test lift return in match unavailable`() = checkFixIsUnavailable("Lift return out of 'if'", """
        fn foo(x: bool) -> i32 {
            match x/*caret*/ {
                true => return 1,
                false => {
                    println("Oops");
                }
            }
            return 0;
        }
    """, checkWeakWarn = true)

    fun `test lift return from nested blocks`() = checkFixByText("Lift return out of 'if'", """
        fn foo(x: bool, y: bool, z: bool) -> i32 {
            /*weak_warning descr="Return can be lifted out of 'if'"*/if/*caret*//*weak_warning**/ x {
                /*weak_warning descr="Return can be lifted out of 'match'"*/match/*weak_warning**/ y {
                    true => return 1,
                    false => {
                        return 2;
                    }
                }
            } else {
                /*weak_warning descr="Return can be lifted out of 'if'"*/if/*weak_warning**/ z {
                    return 3;
                } else {
                    {
                        return 4
                    }
                }
            }
        }
    """, """
        fn foo(x: bool, y: bool, z: bool) -> i32 {
            return if x {
                match y {
                    true => 1,
                    false => {
                        2
                    }
                }
            } else {
                if z {
                    3
                } else {
                    {
                        4
                    }
                }
            }
        }
    """, checkWeakWarn = true)

    fun `test inspection doesn't trigger on return without expression`() = checkFixIsUnavailable("Lift return out of 'if'", """
        fn foo(x: i32) {
            if/*caret*/ x < 0 {
                println!("< 0");
                return;
            } else {
                println!("> 0");
                return;
            }
        }
    """, checkWeakWarn = true)

    fun `test keep comments`() = checkFixByText("Lift return out of 'if'", """
        fn foo(x: i32) -> bool {
            /*weak_warning descr="Return can be lifted out of 'if'"*/if/*caret*//*weak_warning**/ x < 0 {
                return true; // comment 1
            } else {
                return false // comment 2
            }
        }
    """, """
        fn foo(x: i32) -> bool {
            return if x < 0 {
                true // comment 1
            } else {
                false // comment 2
            }
        }
    """, checkWeakWarn = true)

    fun `test lift return in empty match unavailable`() = checkFixIsUnavailable("Lift return out of 'match'", """
        fn foo() {
            match/*caret*/ true {
            }
            let x = 1;
        }
    """, checkWeakWarn = true)
}
