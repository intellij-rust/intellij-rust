/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring

import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsFile

class RsIntroduceVariableHandlerTest : RsTestBase() {
    override val dataPath = "org/rust/lang/refactoring/fixtures/introduce_variable/"

    fun testExpression() = doTest("""
        fn hello() {
            foo(5 + /*caret*/10);
        }""", """
        fn hello() {
            let i = 10;
            foo(5 + i);
        }""")
    {
        val ref = refactoring()
        val expr = ref.getTarget(0, 3)
        ref.replaceElementForAllExpr(expr, listOf(expr))
    }

    fun testExplicitSelectionWorks() {
        myFixture.configureByText("main.rs", """
            fn main() { 1 + <selection>1</selection>;}
        """)
        refactoring().getTarget(0, 1)
    }

    fun testMultipleOccurrences() = doTest("""
        fn hello() {
            foo(5 + /*caret*/10);
            foo(5 + 10);
        }

        fn foo(x: Int) {

        }""", """
        fn hello() {
            let x = 5 + 10;
            foo(x);
            foo(x);
        }

        fn foo(x: Int) {

        }""")
    {
        val ref = refactoring()
        val expr = ref.getTarget(1, 3)
        val occurrences = findOccurrences(expr)
        ref.replaceElementForAllExpr(expr, occurrences)
    }

    fun testMultipleOccurrences2() = doTest("""
        fn main() {
            let a = 1;
            let b = a + 1;
            let c = a +/*caret*/ 1;
        }""", """
        fn main() {
            let a = 1;
            let x = a + 1;
            let b = x;
            let c = x;
        }""")
    {
        val ref = refactoring()
        val expr = ref.getTarget(0, 1)
        val occurrences = findOccurrences(expr)
        ref.replaceElementForAllExpr(expr, occurrences)
    }


    fun testCaretAfterElement() = doTest("""
        fn main() {
            1/*caret*/;
        }""", """
        fn main() {
            let i = 1;
        }""")
    {
        val ref = refactoring()
        val expr = ref.getTarget(0, 1)
        ref.replaceElement(expr, listOf(expr))
    }

    fun testTopLevelInBlock() = doTest("""
        fn main() {
            let _ = {
                1/*caret*/
            };
        }""", """
        fn main() {
            let _ = {
                let i = 1;
            };
        }""")
    {
        val ref = refactoring()
        val expr = ref.getTarget(0, 1)
        ref.replaceElement(expr, listOf(expr))
    }

    fun testStatement() = doTest("""
        fn hello() {
            foo(5 + /*caret*/10);
        }""", """
        fn hello() {
            let foo = foo(5 + 10);
        }""")
    {
        val ref = refactoring()
        val expr = ref.getTarget(2, 3)
        ref.replaceElement(expr, listOf(expr))
    }

    fun testMatch() = doTest("""
        fn bar() {
            ma/*caret*/tch 5 {
                2 => 2,
                _ => 8,
            };
        }""", """
        fn bar() {
            let i = match 5 {
                2 => 2,
                _ => 8,
            };
        }""")
    {
        val ref = refactoring()
        val expr = ref.getTarget(0, 1)
        ref.replaceElement(expr, listOf(expr))
    }

    fun testFile() = doTest("""
        fn read_fle() -> Result<Vec<String, io::Error>> {
            File::op/*caret*/en("res/input.txt")?
        }""", """
        fn read_fle() -> Result<Vec<String, io::Error>> {
            let x = File::open("res/input.txt")?;
        }""")
    {
        val ref = refactoring()
        val expr = ref.getTarget(1, 2)
        ref.replaceElement(expr, listOf(expr))
    }

    fun testRefMut() = doTest("""
        fn read_file() -> Result<String, Error> {
            let file = File::open("res/input.txt")?;

            file.read_to_string(&mut String:/*caret*/:new())
        }""", """
        fn read_file() -> Result<String, Error> {
            let file = File::open("res/input.txt")?;
            let mut string = String::new();

            file.read_to_string(&mut string)
        }""")
    {
        val ref = refactoring()
        val expr = ref.getTarget(0, 3)
        ref.replaceElement(expr, listOf(expr))
    }

    private fun doTest(@Language("Rust") before: String, @Language("Rust") after: String, action: () -> Unit) {
        InlineFile(before).withCaret()
        openFileInEditor("main.rs")
        action()
        myFixture.checkResult(after)
    }

    private fun refactoring(): RsIntroduceVariableRefactoring =
        RsIntroduceVariableRefactoring(project, myFixture.editor, myFixture.file as RsFile)

    fun RsIntroduceVariableRefactoring.getTarget(idx: Int, total: Int): RsExpr {
        check(idx < total) { "Can't select $idx target out of $total" }
        val targets = possibleTargets()
        check(targets.size == total) {
            "Expected $total targets, got ${targets.size}:\n\n${targets.map { it.text }.joinToString("\n\n")}"
        }
        return targets[idx]
    }
}
