/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.implementMembers

import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase


class ImplementMembersHandlerTest : RsTestBase() {
    fun `test available via override shortcut`() = invokeVia {
        myFixture.performEditorAction("ImplementMethods")
    }

    fun `test available via quick fix`() = invokeVia {
        val action = myFixture.findSingleIntention("Implement members")
        myFixture.launchAction(action)
    }

    private fun invokeVia(actionInvoker: () -> Unit) {
        checkByText("""
            trait T { fn f1(); }
            struct S;
            impl T /*caret*/for S {}
        """, """
            trait T { fn f1(); }
            struct S;
            impl T for S {
                fn f1() {
                    unimplemented!()
                }
            }
        """) {
            withMockTraitMemberChooser({ _, all, _ -> all }) {
                actionInvoker()
            }
        }
    }


    fun `test not available outside of impl`() {
        InlineFile("""
            trait T { fn f1(); }
            struct /*caret*/S;
            impl T for S {}
        """)
        ImplementMembersMarks.noImplInHandler.checkHit {
            val presentation = myFixture.testAction(ActionManagerEx.getInstanceEx().getAction("ImplementMethods"))
            check(!presentation.isEnabled)
        }
        check(myFixture.filterAvailableIntentions("Implement members").isEmpty())
    }

    fun `test implement methods`() = doTest("""
        trait T {
            fn f1();
            fn f2();
            fn f3() {}
            fn f4() {}
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f1()", true, true),
        ImplementMemberSelection("f2()", true, false),
        ImplementMemberSelection("f3()", false, false),
        ImplementMemberSelection("f4()", false, true)
    ), """
        trait T {
            fn f1();
            fn f2();
            fn f3() {}
            fn f4() {}
        }
        struct S;
        impl T for S {
            fn f1() {
                unimplemented!()
            }

            fn f4() {
                unimplemented!()
            }
        }
    """)

    fun `test implement unsafe methods`() = doTest("""
        trait T {
            unsafe fn f1();
            unsafe fn f2();
            unsafe fn f3() {}
            unsafe fn f4() {}
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f1()", true, true),
        ImplementMemberSelection("f2()", true, false),
        ImplementMemberSelection("f3()", false, false),
        ImplementMemberSelection("f4()", false, true)
    ), """
        trait T {
            unsafe fn f1();
            unsafe fn f2();
            unsafe fn f3() {}
            unsafe fn f4() {}
        }
        struct S;
        impl T for S {
            unsafe fn f1() {
                unimplemented!()
            }

            unsafe fn f4() {
                unimplemented!()
            }
        }
    """)

    fun `test implement more methods`() = doTest("""
        trait T {
            fn f1(a: i8, b: i16, c: i32, d: i64);
            fn f2(a: (i32, u32));
            fn f3(u32, u64);
            fn f4() -> bool;
            fn f5(a: f64, b: bool) -> (i8, u8);
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f1(a: i8, b: i16, c: i32, d: i64)", true),
        ImplementMemberSelection("f2(a: (i32, u32))", true),
        ImplementMemberSelection("f3(u32, u64)", true),
        ImplementMemberSelection("f4() -> bool", true),
        ImplementMemberSelection("f5(a: f64, b: bool) -> (i8, u8)", true)
    ), """
        trait T {
            fn f1(a: i8, b: i16, c: i32, d: i64);
            fn f2(a: (i32, u32));
            fn f3(u32, u64);
            fn f4() -> bool;
            fn f5(a: f64, b: bool) -> (i8, u8);
        }
        struct S;
        impl T for S {
            fn f1(a: i8, b: i16, c: i32, d: i64) {
                unimplemented!()
            }

            fn f2(a: (i32, u32)) {
                unimplemented!()
            }

            fn f3(_: u32, _: u64) {
                unimplemented!()
            }

            fn f4() -> bool {
                unimplemented!()
            }

            fn f5(a: f64, b: bool) -> (i8, u8) {
                unimplemented!()
            }
        }
    """)

    fun `test implement types`() = doTest("""
        trait T {
            type T1;
            type T2;
            type T3 = i32;
            type T4 = f64;
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("T1", true, true),
        ImplementMemberSelection("T2", true, false),
        ImplementMemberSelection("T3", false, false),
        ImplementMemberSelection("T4", false, true)
    ), """
        trait T {
            type T1;
            type T2;
            type T3 = i32;
            type T4 = f64;
        }
        struct S;
        impl T for S {
            type T1 = ();
            type T4 = ();
        }
    """)

    fun `test implement constants`() = doTest("""
        trait T {
            const C1: i32;
            const C2: f64;
            const C3: &'static str = "foo";
            const C4: &'static str = "bar";
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("C1: i32", true, true),
        ImplementMemberSelection("C2: f64", true, false),
        ImplementMemberSelection("C3: &'static str", false, false),
        ImplementMemberSelection("C4: &'static str", false, true)
    ), """
        trait T {
            const C1: i32;
            const C2: f64;
            const C3: &'static str = "foo";
            const C4: &'static str = "bar";
        }
        struct S;
        impl T for S {
            const C1: i32 = unimplemented!();
            const C4: &'static str = unimplemented!();
        }
    """)

    fun `test implement all`() = doTest("""
        trait T {
            fn f1();
            type T1;
            const C1: i32;
            fn f2() {}
            type T2 = f64;
            const C2: f64 = 4.2;
        }
        struct S;
        impl T for S {/*caret*/}
    """, listOf(
        ImplementMemberSelection("f1()", true),
        ImplementMemberSelection("T1", true),
        ImplementMemberSelection("C1: i32", true),
        ImplementMemberSelection("f2()", false),
        ImplementMemberSelection("T2", false),
        ImplementMemberSelection("C2: f64", false)

    ), """
        trait T {
            fn f1();
            type T1;
            const C1: i32;
            fn f2() {}
            type T2 = f64;
            const C2: f64 = 4.2;
        }
        struct S;
        impl T for S {
            const C1: i32 = unimplemented!();
            type T1 = ();

            fn f1() {
                unimplemented!()
            }
        }
    """)

    fun `test do not implement methods already present`() = doTest("""
        trait T {
            fn f1();
            fn f2();
            fn f3() {}
        }
        struct S;
        impl T for S {
            fn f1() { }
            /*caret*/
        }
    """, listOf(
        ImplementMemberSelection("f2()", true, true),
        ImplementMemberSelection("f3()", false, false)
    ), """
        trait T {
            fn f1();
            fn f2();
            fn f3() {}
        }
        struct S;
        impl T for S {
            fn f1() { }
            fn f2() {
                unimplemented!()
            }
        }
    """)

    private data class ImplementMemberSelection(val member: String, val byDefault: Boolean, val isSelected: Boolean = byDefault)

    private fun doTest(@Language("Rust") code: String,
                       chooser: List<ImplementMemberSelection>,
                       @Language("Rust") expected: String) {

        checkByText(code.trimIndent(), expected.trimIndent()) {
            withMockTraitMemberChooser({ _, all, selectedByDefault ->
                TestCase.assertEquals(all.map { it.formattedText() }, chooser.map { it.member })
                TestCase.assertEquals(selectedByDefault.map { it.formattedText() }, chooser.filter { it.byDefault }.map { it.member })
                extractSelected(all, chooser)
            }) {
                myFixture.performEditorAction("ImplementMethods")
            }
        }
    }

    private fun extractSelected(all: List<RsTraitMemberChooserMember>, chooser: List<ImplementMemberSelection>): List<RsTraitMemberChooserMember> {
        val selected = chooser.filter { it.isSelected }.map { it.member }
        return all.filter { selected.contains(it.formattedText()) }
    }

}
