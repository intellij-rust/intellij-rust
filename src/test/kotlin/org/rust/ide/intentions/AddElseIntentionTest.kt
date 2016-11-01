package org.rust.ide.intentions

import org.intellij.lang.annotations.Language
import org.rust.lang.RustTestCaseBase

class AddElseIntentionTest : RustTestCaseBase() {
    override val dataPath: String = ""

    fun test1() =
        doUnavailableTest(
            """
            fn main() {
                42/*caret*/;
            }
            """
        )

    fun testFullIfElse() =
        doUnavailableTest(
            """
            fn foo(a: i32, b: i32) {
                if a == b {
                    println!("Equally");/*caret*/
                } else {
                    println!("Not equally");
                }
            }
            """
        )

    fun testSimple() =
        doAvailableTest(
            """
            fn foo(a: i32, b: i32) {
                if a == b {
                    println!("Equally");/*caret*/
                }
            }
            """,
            """
            fn foo(a: i32, b: i32) {
                if a == b {
                    println!("Equally");
                } else {/*caret*/}
            }
            """
        )

    fun testNested1() =
        doAvailableTest(
            """
            fn main() {
                if true {
                    if true {
                        42
                    /*caret*/}
                }
            }
            """,
            """
            fn main() {
                if true {
                    if true {
                        42
                    } else {/*caret*/}
                }
            }
            """
        )

    fun testNested2() =
        doAvailableTest(
            """
            fn main() {
                if true {
                    if true {
                        42
                    }/*caret*/
                }
            }
            """,
            """
            fn main() {
                if true {
                    if true {
                        42
                    }
                } else {/*caret*/}
            }
            """
        )

    fun testReformat() =
        doAvailableTest(
            """
            fn main() {
                if true {
                /*caret*/
                }
            }
            """,
            """
            fn main() {
                if true {} else {/*caret*/}
            }
            """
        )

    private fun doAvailableTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        InlineFile(before).withCaret()
        myFixture.launchAction(AddElseIntention())
        myFixture.checkResult(after.replace("/*caret*/", "<caret>"))
    }

    private fun doUnavailableTest(@Language("Rust") before: String) {
        InlineFile(before).withCaret()
        myFixture.launchAction(AddElseIntention())
        myFixture.checkResult(before.replace("/*caret*/", "<caret>"))
    }
}
