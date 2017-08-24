/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.intellij.lang.annotations.Language
import org.rust.ide.annotator.RsAnnotatorTestBase

class AddTurbofishFixTest : RsAnnotatorTestBase() {
    private val intention = "Add turbofish operator"

    fun `test trivial happy path`() = checkStatement(
        """std::parse</*caret*/i32>("42")""",
        """std::parse::</*caret*/i32>("42")"""
    )


    fun `test should not be applied`() = checkNoIntention("""1 < 5 /*carret*/> 3;""")

    fun `test should be available also in right side`() = checkStatement(
            """parse<i32>(/*caret*/"42")""",
            """parse::<i32>(/*caret*/"42")""")

    fun `test should be available also when call is chained by another function`() =
        checkStatement(
            """parse<i32>(/*caret*/"42").unwrap()""",
            """parse::<i32>(/*caret*/"42").unwrap()""")

    fun `test should recognize also generics arguments`() {
        checkStatement(
            """something<Option<String>>(/*caret*/"42")""",
            """something::<Option<String>>(/*caret*/"42")""")
        checkStatement(
            """something<Option<Option<String>>>(/*caret*/"42")""",
            """something::<Option<Option<String>>>(/*caret*/"42")""")
        checkStatement(
            """something<Option<Option<Option<String>>>>(/*caret*/"42")""",
            """something::<Option<Option<Option<String>>>>(/*caret*/"42")""")
        checkStatement(
            """something<Option<Option<Option<Option<String>>>>>(/*caret*/"42")""",
            """something::<Option<Option<Option<Option<String>>>>>(/*caret*/"42")""")
    }

    fun `test should guess the correct boundary`() {
        checkStatement(
            """something<Option<i32>>(/*caret*/"42") >> 3""",
            """something::<Option<i32>>(/*caret*/"42") >> 3""")
        checkStatement(
            """something<Option<i32>>(/*caret*/"42") > 3 > 5""",
            """something::<Option<i32>>(/*caret*/"42") > 3 > 5""")
        checkStatement(
            """something<Option<i32>>(/*caret*/"42") == call_something()""",
            """something::<Option<i32>>(/*caret*/"42") == call_something()""")
        checkStatement(
            """3 < something<Option<i32>>(/*caret*/"42")""",
            """3 < something::<Option<i32>>(/*caret*/"42")""")
        checkStatement(
            """3 == something<Option<i32>>(/*caret*/"42")""",
            """3 == something::<Option<i32>>(/*caret*/"42")""")
        checkStatement(
            """bye() == 3 < something<Option<i32>>(/*caret*/"42") > more() >> 2""",
            """bye() == 3 < something::<Option<i32>>(/*caret*/"42") > more() >> 2""")
    }


    fun `test should not be available if the instance is corrected yet`() =
        checkNoIntention("""parse::</*caret*/i32>("42")""")

    fun `test should not trigger misspelled comparison sentences`() {
        checkNoIntention("""1 < /*caret*/x < 5""")
        checkNoIntention("""1 < /*caret*/x > 5""")
    }

    fun `test should be available just if looks like a generic reference`() {
        checkNoIntention("""parse>/*caret*/i32>("42")""")
        checkNoIntention("""parse</*caret*/i32<("42")""")
    }

    private fun checkNoIntention(code: String) {
        InlineFile(wrapStatement(code))

        check(intention !in myFixture.availableIntentions.mapNotNull { it.familyName }) {
            "\"$intention\" shouldn't be in intentions list"
        }
    }

    private fun checkStatement(before: String, after: String) =
        checkQuickFix(wrapStatement(before), wrapStatement(after))

    private fun wrapStatement(statement: String) =
        """
            fn foo(x: i32) {
                let _ = $statement;
            }
        """

    private fun checkQuickFix(@Language("Rust") before: String, @Language("Rust") after: String) =
        checkQuickFix(intention, before, after)

}
