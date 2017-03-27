package org.rust.ide.annotator.fixes

import org.intellij.lang.annotations.Language
import org.rust.ide.annotator.RsAnnotatorTestBase

class AddTurbofishFixTest : RsAnnotatorTestBase() {
    val intention = "Add turbofish operator"

    fun `test trivial happy path`() = checkQuickFix("""
        fn main() {
            let _ = std::parse</*caret*/i32>("42");
        }
    """, """
        fn main() {
            let _ = std::parse::</*caret*/i32>("42");
        }
    """)

    fun `test should not be applied`() = checkNoIntention(intention,
        """
        fn main() {
            let my_bool = 1 < 5 /*carret*/> 3;
        }
        """)

    fun `test should be available also in right side`() = checkQuickFix(
        """
        fn foo() {
            let _ = parse<i32>(/*caret*/"42");
        }
        """,
        """
        fn foo() {
            let _ = parse::<i32>(/*caret*/"42");
        }
        """)

    fun `test should be available also when call is chained by another function`() = checkQuickFix(
        """
        fn foo() {
            let _ = parse<i32>(/*caret*/"42").unwrap();
        }
        """,
        """
        fn foo() {
            let _ = parse::<i32>(/*caret*/"42").unwrap();
        }
        """)

    fun `test should recognize also generics arguments`() {
        for (gen in listOf("<Option<String>>",
            "<Option<Option<String>>>",
            "<Option<Option<Option<String>>>>",
            "<Option<Option<Option<Option<String>>>>>")) {
            checkQuickFix(
                """
                    fn foo() {
                        let _ = something$gen(/*caret*/"42");
                    }
                """, """
                    fn foo() {
                        let _ = something::$gen(/*caret*/"42");
                    }
                """)
        }
    }

    fun `test should guess the correct boundary`() {
        val cases = listOf(
            Pair("", " >> 3"), Pair("", " > 3 > 5"), Pair("", " == call_something()"),
            Pair("3 < ", ""), Pair("3 == ", ""), Pair("bye() == 3 < ", " > more() >> 2")
        )

        for ((before, after) in cases) {
            checkQuickFix(
                """
                fn foo() {
                    let _ = ${before}something<Option<i32>>(/*caret*/"42")$after;
                }
                """, """
                fn foo() {
                    let _ = ${before}something::<Option<i32>>(/*caret*/"42")$after;
                }
                """)

        }
    }


    fun `test should not be available if the instance is corrected yet`() = checkNoIntention(intention,
        """
        fn foo() {
            let _ = parse::</*caret*/i32>("42");
        }
        """)

    fun `test should not trigger misspelled comparison sentences`() {
        checkNoIntention(intention,
            """
            fn foo(x: i32) {
                let _ = 1 < /*caret*/x < 5;
            }
            """)
        checkNoIntention(intention,
            """
            fn foo(x: i32, y: i32) {
                let _ = 1 < /*caret*/x > 5;
            }
            """)
    }

    fun `test should be available just if looks like a generic reference`() {
        checkNoIntention(intention,
            """
            fn foo(x: i32) {
                let _ = parse>/*caret*/i32>("42");
            }
            """)
        checkNoIntention(intention,
            """
            fn foo(x: i32) {
                let _ = parse</*caret*/i32<("42");
            }
            """)
    }

    private fun checkNoIntention(name: String, @Language("Rust") code: String) {
        InlineFile(code)

        check(name !in myFixture.availableIntentions.mapNotNull { it.familyName }) {
            "\"$name\" shouldn't be in intentions list"
        }
    }

    private fun checkQuickFix(@Language("Rust") before: String, @Language("Rust") after: String) =
        checkQuickFix(intention, before, after)

}
