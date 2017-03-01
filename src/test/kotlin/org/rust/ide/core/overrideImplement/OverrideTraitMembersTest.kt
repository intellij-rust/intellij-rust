package org.rust.ide.core.overrideImplement

import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.util.parentOfType
import java.util.*

class OverrideTraitMembersTest : RsTestBase() {
    override val dataPath = ""

    fun test1() = doTest("""
        trait T {
            fn foo();
            fn bar() {}
        }
        struct S;
        impl T for S {
            /*caret*/
        }
    """, """
        *s foo()
           bar()
    """, """
        
    """)

    private fun doTest(@Language("Rust") code: String,
                       chooser: String,
                       @Language("Rust") expected: String) {
        checkByText(code, expected) {
            val impl = myFixture.elementAtCaret.parentOfType<RsImplItem>()
                    ?: fail("Caret is not in an impl block")
            val (all, selected) = createTraitMembersChooser(impl)
                    ?: fail("No members are available")
            val defaultChooser = renderChooser(all, selected)
            TestCase.assertEquals(unselectChooser(chooser), defaultChooser)
            val chooserSelected = extractSelected(all, chooser)
            insertNewTraitMembers(chooserSelected, impl)
        }
    }

    private fun extractSelected(all: List<RsTraitMemberChooserMember>, chooser: String): List<RsTraitMemberChooserMember> {
        val boolSelection = chooser.split("\n").map { it[1] == 's' }
        TestCase.assertEquals(all.size, boolSelection.size)
        val result = ArrayList<RsTraitMemberChooserMember>()
        for (i in 0..all.size - 1) {
            if (boolSelection[i]) {
                result.add(all[i])
            }
        }
        return result
    }

    private fun unselectChooser(chooser: String) = chooser.split("\n").map {
        if (it.length >= 2)
            "" + it[0] + ' ' + it.substring(2)
        else
            it
    }.joinToString("\n")

    private fun fail(message: String): Nothing {
        TestCase.fail(message)
        error("Test failed with message: \"$message\"")
    }

    private fun renderChooser(all: Collection<RsTraitMemberChooserMember>,
                              selected: Collection<RsTraitMemberChooserMember>): String {
        val selectedSet = HashSet(selected)
        val builder = StringBuilder()
        for (member in all) {
            if (member in selectedSet)
                builder.append("*  ")
            else
                builder.append("   ")
            builder.append(member.formattedText()).append("\n")
        }
        builder.deleteCharAt(builder.lastIndex)
        return builder.toString()
    }
}