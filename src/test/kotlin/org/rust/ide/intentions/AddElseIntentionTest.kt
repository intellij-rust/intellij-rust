package org.rust.ide.intentions

class AddElseIntentionTest : RsIntentionTestBase(AddElseIntention()) {

    fun test1() = doUnavailableTest("""
        fn main() {
            42/*caret*/;
        }
    """)

    fun testFullIfElse() = doUnavailableTest("""
        fn foo(a: i32, b: i32) {
            if a == b {
                println!("Equally");/*caret*/
            } else {
                println!("Not equally");
            }
        }
    """)

    fun testSimple() = doAvailableTest("""
        fn foo(a: i32, b: i32) {
            if a == b {
                println!("Equally");/*caret*/
            }
        }
    """, """
        fn foo(a: i32, b: i32) {
            if a == b {
                println!("Equally");
            } else {/*caret*/}
        }
    """)

    fun `test caret after brace`() = doAvailableTest("""
        fn foo() {
            if true {
                ()
            }/*caret*/
        }
    """, """
        fn foo() {
            if true {
                ()
            } else {/*caret*/}
        }
    """)

    fun testNested1() = doAvailableTest("""
        fn main() {
            if true {
                if true {
                    42
                /*caret*/}
            }
        }
    """, """
        fn main() {
            if true {
                if true {
                    42
                } else {/*caret*/}
            }
        }
    """)

    fun testNested2() = doAvailableTest("""
        fn main() {
            if true {
                if true {
                    42
                }/*caret*/
            }
        }
    """, """
        fn main() {
            if true {
                if true {
                    42
                }
            } else {/*caret*/}
        }
    """)

    fun testReformat() = doAvailableTest("""
        fn main() {
            if true {
            /*caret*/
            }
        }
    """, """
        fn main() {
            if true {} else {/*caret*/}
        }
    """)
}
