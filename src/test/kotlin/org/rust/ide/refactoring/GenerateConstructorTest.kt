/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.openapi.project.Project
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.ide.refactoring.generateConstructor.RsStructMemberChooserObject
import org.rust.ide.refactoring.generateConstructor.StructMemberChooserUi
import org.rust.ide.refactoring.generateConstructor.withMockStructMemberChooserUi

class GenerateConstructorTest : RsTestBase() {

    fun `test generic struct`() = doTest("""
        struct S<T> {
            n: i32,
            m: T
        }
    """, listOf(
        ConstructorArgumentsSelection("n: i32", true),
        ConstructorArgumentsSelection("m: T", true)
    ), """
        struct S<T> {
            n: i32,
            m: T
        }

        impl<T> S<T> {
            pub fn new(n: i32, m: T) -> Self {
                S { n, m }
            }
        }
    """)

    fun `test empty type declaration`() = doTest("""
        struct S {
            n: i32,
            m:
        }
    """, listOf(
        ConstructorArgumentsSelection("n: i32", true),
        ConstructorArgumentsSelection("m: ()", true)
    ), """
        struct S {
            n: i32,
            m:
        }

        impl S {
            pub fn new(n: i32, m: ()) -> Self {
                S { n, m }
            }
        }
    """)

    fun `test empty struct`() = doTest("""
        struct S {}
    """, emptyList(), """
        struct S {}

        impl S {
            pub fn new() -> Self {
                S {}
            }
        }
    """)

    fun `test tuple struct`() = doTest("""
        struct Color(i32, i32, i32)/*caret*/;
    """, listOf(
        ConstructorArgumentsSelection("field0: i32", true),
        ConstructorArgumentsSelection("field1: i32", true),
        ConstructorArgumentsSelection("field2: i32", true)
    ), """
        struct Color(i32, i32, i32);

        impl Color {
            pub fn new(field0: i32, field1: i32, field2: i32) -> Self {
                Color(field0, field1, field2)
            }
        }
    """)

    fun `test select none fields`() = doTest("""
        struct S {
            n: i32,/*caret*/
            m: i64,
        }
    """, listOf(
        ConstructorArgumentsSelection("n: i32", false),
        ConstructorArgumentsSelection("m: i64", false)
    ), """
        struct S {
            n: i32,
            m: i64,
        }

        impl S {
            pub fn new() -> Self {
                S { n: (), m: () }
            }
        }
    """)

    fun `test select all fields`() = doTest("""
        struct S {
            n: i32,/*caret*/
            m: i64,
        }
    """, listOf(
        ConstructorArgumentsSelection("n: i32", true),
        ConstructorArgumentsSelection("m: i64", true)
    ), """
        struct S {
            n: i32,
            m: i64,
        }

        impl S {
            pub fn new(n: i32, m: i64) -> Self {
                S { n, m }
            }
        }
    """)

    fun `test select some fields`() = doTest("""
        struct S {
            n: i32,/*caret*/
            m: i64,
        }
    """, listOf(
        ConstructorArgumentsSelection("n: i32", true),
        ConstructorArgumentsSelection("m: i64", false)
    ), """
        struct S {
            n: i32,
            m: i64,
        }

        impl S {
            pub fn new(n: i32) -> Self {
                S { n, m: () }
            }
        }
    """)

    private data class ConstructorArgumentsSelection(val member: String, val isSelected: Boolean)

    private fun doTest(
        @Language("Rust") code: String,
        chooser: List<ConstructorArgumentsSelection>,
        @Language("Rust") expected: String
    ) {
        withMockStructMemberChooserUi(object : StructMemberChooserUi {
            override fun selectMembers(project: Project, all: List<RsStructMemberChooserObject>): List<RsStructMemberChooserObject>? {
                assertEquals(all.map { it.text }, chooser.map { it.member })
                val selected = chooser.filter { it.isSelected }.map { it.member }
                return all.filter { it.text in selected }
            }
        }) {
            checkByText(code.trimIndent(), expected.trimIndent()) {
                myFixture.performEditorAction("Rust.GenerateConstructor")
            }
        }
    }
}
