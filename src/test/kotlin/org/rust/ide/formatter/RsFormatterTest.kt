package org.rust.ide.formatter

import org.rust.ide.formatter.settings.RsCodeStyleSettings
import org.rust.lang.RsLanguage

class RsFormatterTest : RsFormatterTestBase() {
    fun testBlocks() = doTest()
    fun testItems() = doTest()
    fun testExpressions() = doTest()
    fun testArgumentAlignment() = doTest()
    fun testArgumentIndent() = doTest()
    fun testTraits() = doTest()
    fun testTupleAlignment() = doTest()
    fun testChainCallAlignmentOff() = doTest()
    fun testChainCallIndent() = doTest()

    fun testChainCallAlignment() {
        common().ALIGN_MULTILINE_CHAINED_METHODS = true
        doTest()
    }

    fun testAlignParamsOff() {
        common().ALIGN_MULTILINE_PARAMETERS = false
        doTest()
    }

    fun testAlignParamsInCallsOff() {
        common().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false
        doTest()
    }

    fun testAlignRetOff() {
        custom().ALIGN_RET_TYPE = false
        doTest()
    }

    fun testAlignWhereOn() {
        custom().ALIGN_WHERE_CLAUSE = true
        doTest()
    }

    fun testAlignWhereBoundsOff() {
        custom().ALIGN_WHERE_BOUNDS = false
        doTest()
    }

    fun testAlignTypeParamsOn() {
        custom().ALIGN_TYPE_PARAMS = true
        doTest()
    }

    fun testMinNumberOfBlankLines() {
        custom().MIN_NUMBER_OF_BLANKS_BETWEEN_ITEMS = 2
        doTest()
    }

    fun testAlignReturnType() = doTest()

    fun testAllowOneLineMatchOff() = doTest()
    fun testAllowOneLineMatch() {
        custom().ALLOW_ONE_LINE_MATCH = true
        doTest()
    }

    fun testMacroUse() = doTest()
    fun testAttributes() = doTest()

    // FIXME: these two guys are way too big
    fun testSpacing() = doTest()

    fun testIssue451() = doTest()   // https://github.com/intellij-rust/intellij-rust/issues/451
    fun testIssue526() = doTest()   // https://github.com/intellij-rust/intellij-rust/issues/526
    fun testIssue569() = doTest()   // https://github.com/intellij-rust/intellij-rust/issues/569

    // https://github.com/intellij-rust/intellij-rust/issues/543
    fun testIssue543a() = doTest()

    fun testIssue543b() = doTest()
    fun testIssue543c() = doTest()

    fun testElse() = doTest()

    fun testIssue654() = doTest()

    fun testUseRoot() = doTest() // https://github.com/intellij-rust/intellij-rust/issues/746

    fun testSpecialMacros() = doTest()

    fun testImportGlobAlignment() = doTextTest("""
        use piston_window::{Button,
        Transformed};
    """, """
        use piston_window::{Button,
                            Transformed};
    """)

    fun `test string literals are left intact`() = doTextTest("""
        fn main() {
        (
        r"
        x
        "
        );
            let foo =
              "Hello
                World";
        }
    """, """
        fn main() {
            (
                r"
        x
        "
            );
            let foo =
                "Hello
                World";
        }
    """)

    fun `test string literals are left intact in macro`() = doTextTest("""
        fn а() {
            println!("{}",
        r##"Some string
        which continues on this line
        and also on this one
        "##);
        }
    """, """
        fn а() {
            println!("{}",
                     r##"Some string
        which continues on this line
        and also on this one
        "##);
        }
    """)

    fun `test let indent`() = doTextTest("""
        fn main() {
            let _ =
            92;
        }
    """, """
        fn main() {
            let _ =
                92;
        }
    """)

    private fun common() = getSettings(RsLanguage)
    private fun custom() = settings.getCustomSettings(RsCodeStyleSettings::class.java)

}
