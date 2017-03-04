package org.rust.ide.annotator.fixes

import org.intellij.lang.annotations.Language
import junit.framework.Assert
import org.rust.ide.annotator.RsAnnotatorTestBase

class AddTurbofishOperatorFixTest : RsAnnotatorTestBase() {
    val intention = "Add turbofish operator"

    fun testTrivialHappyPath() = checkQuickFix("""
        fn main() {
            let _ = std::parse</*caret*/i32>("42");
        }
    """, """
        fn main() {
            let _ = std::parse::</*caret*/i32>("42");
        }
    """)

    fun testShouldNotBeApplied() = checkNoItentnion(intention,
        """
        fn main() {
            let my_bool = 1 < 5 /*carret*/> 3;
        }
        """)

    private fun checkNoItentnion(name: String, @Language("Rust") code : String) {
        InlineFile(code)

        Assert.assertFalse("'$name' shouldn't be in intentions list",
            name in myFixture.availableIntentions.map {it.text})
    }

    private fun checkQuickFix(@Language("Rust") before: String, @Language("Rust") after: String) =
        checkQuickFix(intention, before, after)
}
