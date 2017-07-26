/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.core.overrideImplement

import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.ext.childOfType
import java.util.*

class ImplementMembersFixTest : RsTestBase() {
    override fun isWriteActionRequired() = true

    fun test1() = doTest("""
        trait T {
            fn foo();
            fn bar() {}
        }
        struct S;
        impl T for S {}
    """, """
        |*s foo()
        |   bar()
    """, """
        trait T {
            fn foo();
            fn bar() {}
        }
        struct S;
        impl T for S {
            fn foo() {
                unimplemented!()
            }
        }
    """)

    fun test2() = doTest("""
        trait T {
            fn foo();
            fn bar() {}
        }
        struct S;
        impl T for S {}
    """, """
        |*s foo()
        | s bar()
    """, """
        trait T {
            fn foo();
            fn bar() {}
        }
        struct S;
        impl T for S {
            fn foo() {
                unimplemented!()
            }

            fn bar() {
                unimplemented!()
            }
        }
    """)

    fun test3() = doTest("""
        trait T {
            type Type;
            fn foo();
            fn bar() {}
        }
        struct S;
        impl T for S {}
    """, """
        |*s Type
        |*s foo()
        |   bar()
    """, """
        trait T {
            type Type;
            fn foo();
            fn bar() {}
        }
        struct S;
        impl T for S {
            type Type = ();

            fn foo() {
                unimplemented!()
            }
        }
    """)

    fun test4() = doTest("""
        trait T {
            type Type;
            type Type2 = i32;
        }
        struct S;
        impl T for S {}
    """, """
        |*s Type
        | s Type2
    """, """
        trait T {
            type Type;
            type Type2 = i32;
        }
        struct S;
        impl T for S {
            type Type = ();
            type Type2 = ();
        }
    """)

    fun test5() = doTest("""
        trait T {
            type Type1;
            type Type2;
            type Type3 = f64;
            const CONST1: i32;
            const CONST2: f64;
            const CONST3: &'static str = "123";
            fn foo(x: i32) -> i32;
            fn bar(f64);
            fn baz() {
                println!("Hello world");
            }
        }
        struct S;
        impl T for S {}
    """, """
        |*s Type1
        |*s Type2
        | s Type3
        |*s CONST1: i32
        |*s CONST2: f64
        | s CONST3: &'static str
        |*s foo(x: i32) -> i32
        |*s bar(f64)
        |   baz()
    """, """
        trait T {
            type Type1;
            type Type2;
            type Type3 = f64;
            const CONST1: i32;
            const CONST2: f64;
            const CONST3: &'static str = "123";
            fn foo(x: i32) -> i32;
            fn bar(f64);
            fn baz() {
                println!("Hello world");
            }
        }
        struct S;
        impl T for S {
            const CONST1: i32 = unimplemented!();
            const CONST2: f64 = unimplemented!();
            const CONST3: &'static str = unimplemented!();
            type Type1 = ();
            type Type2 = ();
            type Type3 = ();

            fn foo(x: i32) -> i32 {
                unimplemented!()
            }

            fn bar(_: f64) {
                unimplemented!()
            }
        }
    """)

    private fun doTest(@Language("Rust") code: String,
                       chooser: String,
                       @Language("Rust") expected: String) {
        checkByText(code.trimIndent(), expected.trimIndent()) {
            val impl = myFixture.file.childOfType<RsImplItem>()
                ?: fail("Caret is not in an impl block")
            val (all, selected) = createTraitMembersChooser(impl)
                ?: fail("No members are available")
            val defaultChooser = renderChooser(all, selected)
            TestCase.assertEquals(unselectChooser(chooser), defaultChooser)
            val chooserSelected = extractSelected(all, chooser)
            insertNewTraitMembers(chooserSelected, impl.members!!)
        }
    }

    private fun extractSelected(all: List<RsTraitMemberChooserMember>, chooser: String): List<RsTraitMemberChooserMember> {
        val boolSelection = chooser.split("\n").filter(String::isNotBlank).map { it.trim()[2] == 's' }
        TestCase.assertEquals(all.size, boolSelection.size)
        val result = (0..all.size - 1)
            .filter { boolSelection[it] }
            .map { all[it] }
        return result
    }

    private fun unselectChooser(chooser: String) = chooser.split("\n").filter(String::isNotBlank).map {
        val s = it.trim()
        if (s.length >= 4)
            "|" + s[1] + "  " + s.substring(4)
        else
            s
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
                builder.append("|*  ")
            else
                builder.append("|   ")
            builder.append(member.formattedText()).append("\n")
        }
        builder.deleteCharAt(builder.lastIndex)
        return builder.toString()
    }
}
