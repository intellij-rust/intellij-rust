/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class SplitIfIntentionTest : RsIntentionTestBase(SplitIfIntention()) {

    fun test1() = doUnavailableTest("""
        fn main() {
            42/*caret*/;
        }
    """)

    fun test2() = doUnavailableTest("""
        fn main() {
            if true =/*caret*/= true && false {}
        }
    """)

    fun `test simple &&`() = doAvailableTest("""
        fn main() {
            if true &/*caret*/& false {
                42
            } else {
                24
            };
        }
    """, """
        fn main() {
            if true {
                if false {
                    42
                } else {
                    24
                }
            } else {
                24
            };
        }
    """)

    fun `test simple ||`() = doAvailableTest("""
        fn main() {
            if true |/*caret*/| false {
                42
            } else {
                24
            };
        }
    """, """
        fn main() {
            if true {
                42
            } else if false {
                42
            } else {
                24
            };
        }
    """)

    fun `test simple && without else`() = doAvailableTest("""
        fn main() {
            if true &/*caret*/& false {
                42
            };
        }
    """, """
        fn main() {
            if true {
                if false {
                    42
                }
            };
        }
    """)

    fun `test simple || without else`() = doAvailableTest("""
        fn main() {
            if true |/*caret*/| false {
                42
            };
        }
    """, """
        fn main() {
            if true {
                42
            } else if false {
                42
            };
        }
    """)

    fun `test multy && 1`() = doAvailableTest("""
        fn main() {
            if true &/*caret*/& false && 1 == 1{
                42
            } else {
                24
            };
        }
    """, """
        fn main() {
            if true {
                if false && 1 == 1 {
                    42
                } else {
                    24
                }
            } else {
                24
            };
        }
    """)

    fun `test multy && 2`() = doAvailableTest("""
        fn main() {
            if true && false &/*caret*/& 1 == 1{
                42
            } else {
                24
            };
        }
    """, """
        fn main() {
            if true && false {
                if 1 == 1 {
                    42
                } else {
                    24
                }
            } else {
                24
            };
        }
    """)

    fun `test multy || 1`() = doAvailableTest("""
        fn main() {
            if true |/*caret*/| false || 1 == 1 {
                42
            } else {
                24
            };
        }
    """, """
        fn main() {
            if true {
                42
            } else if false || 1 == 1 {
                42
            } else {
                24
            };
        }
    """)

    fun `test multy || 2`() = doAvailableTest("""
        fn main() {
            if true || false |/*caret*/| 1 == 1 {
                42
            } else {
                24
            };
        }
    """, """
        fn main() {
            if true || false {
                42
            } else if 1 == 1 {
                42
            } else {
                24
            };
        }
    """)

    fun `test available mix 1`() = doAvailableTest("""
        fn main() {
            if true |/*caret*/| false && 1 == 1 {
                42
            } else {
                24
            };
        }
    """, """
        fn main() {
            if true {
                42
            } else if false && 1 == 1 {
                42
            } else {
                24
            };
        }
    """)

    fun `test available mix 2`() = doAvailableTest("""
        fn main() {
            if false && true |/*caret*/| false && 1 == 1 && true {
                42
            } else {
                24
            };
        }
    """, """
        fn main() {
            if false && true {
                42
            } else if false && 1 == 1 && true {
                42
            } else {
                24
            };
        }
    """)

    fun `test unavailable mix 1`() = doUnavailableTest("""
        fn main() {
            if false && true || false &/*caret*/& 1 == 1 && true {
                42
            } else {
                24
            };
        }
    """)

    fun `test unavailable mix 2`() = doUnavailableTest("""
        fn main() {
            if false && true || false && 1 == 1 &/*caret*/& true {
                42
            } else {
                24
            };
        }
    """)

    fun `test unavailable mix 3`() = doUnavailableTest("""
        fn main() {
            if false &/*caret*/& true || false && 1 == 1 && true {
                42
            } else {
                24
            };
        }
    """)

    fun `test unavailable mix 4`() = doUnavailableTest("""
        fn main() {
            if (false && true |/*caret*/| false) && 1 == 1 && true {
                42
            } else {
                24
            };
        }
    """)

    fun `test unavailable mix 5`() = doUnavailableTest("""
        fn main() {
            if(false &/*caret*/& true && false) && 1 == 1 && true {
                42
            } else {
                24
            };
        }
    """)

    fun `test available mix with body`() = doAvailableTest("""
        fn main() {
            if true |/*caret*/| false && 1 == 1 {
                4;
                8;
                15;
                16;
                23;
                42;
            } else {
                24
            };
        }
    """, """
        fn main() {
            if true {
                4;
                8;
                15;
                16;
                23;
                42;
            } else if false && 1 == 1 {
                4;
                8;
                15;
                16;
                23;
                42;
            } else {
                24
            };
        }
    """)

    fun `test with parenthesis`() = doAvailableTest("""
        fn main() {
            if (((true)) &/*caret*/& false) {
                42;
            }
        }
    """, """
        fn main() {
            if ((true)) {
                if false {
                    42;
                }
            }
        }
    """)

    fun `test without spaces`() = doAvailableTest("""
        fn main() {
            if true&/*caret*/&false {
                42;
            }
        }
    """, """
        fn main() {
            if true {
                if false {
                    42;
                }
            }
        }
    """)

    fun `test with unary expr`() = doAvailableTest("""
        fn main() {
            if (!(1 == 1) &/*caret*/& 42 == 24) {
                42;
            }
        }
    """, """
        fn main() {
            if !(1 == 1) {
                if 42 == 24 {
                    42;
                }
            }
        }
    """)
}
