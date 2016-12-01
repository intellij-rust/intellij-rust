package org.rust.lang.refactoring

import org.intellij.lang.annotations.Language
import org.rust.lang.RustTestCaseBase

class RustLocalVariableHandlerTest : RustTestCaseBase() {
    override val dataPath = "org/rust/lang/refactoring/fixtures/introduce_variable/"

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
        val rustLocalVariableHandler = RustLocalVariableHandler()
        val expr = findExpr(myFixture.file, myFixture.editor.caretModel.offset)?.parent
        val occurrences = findOccurrences(expr!!)
        rustLocalVariableHandler.replaceElementForAllExpr(myFixture.project, myFixture.editor, occurrences)
    }

    fun testExpression() = doTest("""
        fn hello() {
            foo(5 + /*caret*/10);
        }""", """
        fn hello() {
            let x = 10;
            foo(5 + x);
        }""")
    {
        val rustLocalVariableHandler = RustLocalVariableHandler()
        val expr = findExpr(myFixture.file, myFixture.editor.caretModel.offset)
        rustLocalVariableHandler.replaceElementForAllExpr(myFixture.project, myFixture.editor, listOf(expr!!))
    }

    fun testStatement() = doTest("""
        fn hello() {
            foo(5 + /*caret*/10);
        }""", """
        fn hello() {
            let x = foo(5 + 10);
        }""")
    {
        val rustLocalVariableHandler = RustLocalVariableHandler()
        val expr = findExpr(myFixture.file, myFixture.editor.caretModel.offset)
        val exprs = possibleExpressions(expr!!)
        rustLocalVariableHandler.replaceElement(myFixture.project, myFixture.editor, listOf(exprs[2]))
    }

    fun testMatch() = doTest("""
        fn bar() {
            ma/*caret*/tch 5 {
                2 => 2,
                _ => 8,
            };
        }""", """
        fn bar() {
            let x = match 5 {
                2 => 2,
                _ => 8,
            };
        }""")
    {
        val rustLocalVariableHandler = RustLocalVariableHandler()
        val expr = findExpr(myFixture.file, myFixture.editor.caretModel.offset)
        rustLocalVariableHandler.replaceElement(myFixture.project, myFixture.editor, listOf(expr!!))
    }

    fun testFile() = doTest("""
        fn read_fle() -> Result<Vec<String, io::Error>> {
            File::op/*caret*/en("res/input.txt")?
        }""", """
        fn read_fle() -> Result<Vec<String, io::Error>> {
            let x = File::open("res/input.txt")?;
        }""")
    {
        val rustLocalVariableHandler = RustLocalVariableHandler()
        val expr = findExpr(myFixture.file, myFixture.editor.caretModel.offset)
        rustLocalVariableHandler.replaceElement(myFixture.project, myFixture.editor, listOf((possibleExpressions(expr!!)[1])))
    }

    fun testRefMut() = doTest("""
        fn read_file() -> Result<String, Error> {
            let file = File::open("res/input.txt")?;

            file.read_to_string(&mut String:/*caret*/:new())?;

            Ok(x)
        }""", """
        fn read_file() -> Result<String, Error> {
            let file = File::open("res/input.txt")?;
            let mut x = String::new();

            file.read_to_string(&mut x)?;

            Ok(x)
        }""")
    {
        val rustLocalVariableHandler = RustLocalVariableHandler()
        val expr = findExpr(myFixture.file, myFixture.editor.caretModel.offset)
        rustLocalVariableHandler.replaceElement(myFixture.project, myFixture.editor, listOf((possibleExpressions(expr!!)[0])))
    }

    private fun doTest(@Language("Rust") before: String, @Language("Rust") after: String, action: () -> Unit) {
        InlineFile(before).withCaret()
        openFileInEditor("main.rs")
        action()
        myFixture.checkResult(after)
    }
}
