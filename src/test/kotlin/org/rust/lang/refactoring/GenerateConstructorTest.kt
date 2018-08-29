package org.rust.lang.refactoring

import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.refactoring.generateConstructor.RsStructMemberChooserMember
import org.rust.lang.refactoring.generateConstructor.mockStructMemberChooser

class GenerateConstructorTest : RsTestBase() {
    fun testEmtyStruct() = doTest("""
            struct S{}
        """,
        listOf(),
        """
struct S{}

impl S {
    pub fn new() -> Self {
        S {}
    }
}"""
    )

    fun testSelectNoneFields() = doTest("""
            struct S{
                    n :  i32,/*caret*/
                    m :  i64,
            }
        """,
        listOf(
            ConstructorArgumentsSelection("n  :   i32",false),
            ConstructorArgumentsSelection("m  :   i64",false)
        ),
        """
struct S{
        n :  i32,
        m :  i64,
}

impl S {
    pub fn new() -> Self {
        S { n: (), m: () }
    }
}"""
    )

    fun testSelectAllFields() = doTest("""
            struct S{
                    n :  i32,/*caret*/
                    m :  i64,
            }
        """,
        listOf(
            ConstructorArgumentsSelection("n  :   i32",true),
            ConstructorArgumentsSelection("m  :   i64", true)
        ),
        """
struct S{
        n :  i32,
        m :  i64,
}

impl S {
    pub fn new(n: i32, m: i64) -> Self {
        S { n, m }
    }
}"""
    )


    fun testSelectSomeFields() = doTest("""
            struct S{
                    n :  i32,/*caret*/
                    m :  i64,
            }
        """,
        listOf(
            ConstructorArgumentsSelection("n  :   i32",true),
            ConstructorArgumentsSelection("m  :   i64", false)
        ),
        """
struct S{
        n :  i32,
        m :  i64,
}

impl S {
    pub fn new(n: i32) -> Self {
        S { n, m: () }
    }
}"""
    )


    private data class ConstructorArgumentsSelection(val member: String, val isSelected: Boolean )

    private fun doTest(@Language("Rust") code: String,
                       chooser: List<ConstructorArgumentsSelection>,
                       @Language("Rust") expected: String) {

        checkByText(code.trimIndent(), expected.trimIndent()) {
            mockStructMemberChooser({ _, all ->
                TestCase.assertEquals(all.map { it.formattedText() }, chooser.map { it.member })
                extractSelected(all, chooser)
            }) {
                myFixture.performEditorAction("Rust.GenerateConstructor")
            }
        }
    }
    private fun extractSelected(all: List<RsStructMemberChooserMember>, chooser: List<ConstructorArgumentsSelection>): List<RsStructMemberChooserMember> {
        val selected = chooser.filter { it.isSelected }.map { it.member }
        return all.filter { selected.contains(it.formattedText()) }
    }
}
