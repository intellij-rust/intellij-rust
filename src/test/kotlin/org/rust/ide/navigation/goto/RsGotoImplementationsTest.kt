/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

class RsGotoImplementationsTest : RsTestBase() {
    fun `test trait`() = doSingleTargetTest("""
        trait T/*caret*/{
            fn test(&self);
        }
        /// docs
        impl T for (){
            fn test(&self) {}
        }
    """, """
        trait T{
            fn test(&self);
        }
        /// docs
        impl T for /*caret*/(){
            fn test(&self) {}
        }
    """)

    fun `test member`() = doSingleTargetTest("""
        trait T{
            fn test/*caret*/(&self);
        }
        impl T for (){
            fn test(&self) {}
        }
    """, """
        trait T{
            fn test(&self);
        }
        impl T for (){
            fn /*caret*/test(&self) {}
        }
    """)

    fun `test not implemented`() = doSingleTargetTest("""
        trait T{
            fn test/*caret*/(&self) {}
        }
        impl T for (){
        }
    """, """
        trait T{
            fn test/*caret*/(&self) {}
        }
        impl T for (){
        }
    """)

    fun `test multiple targets for method impl`() = doMultipleTargetsTest("""
        struct Foo;
        struct Bar<T>(T);
        struct Baz<T> { x: T }
        trait Trait {
            fn bar/*caret*/();
        }

        impl Trait for Foo {
            fn bar() { todo!() }
        }
        impl<T> Trait for Bar<T> {
            fn bar() { todo!() }
        }
        impl<T> Trait for Baz<T> where T : Clone {
            fn bar() { todo!() }
        }
    """,
        "Trait for Foo test_package",
        "Trait for Bar<T> test_package",
        "Trait for Baz<T> where T: Clone test_package"
    )

    fun `test multiple targets for trait impl`() = doMultipleTargetsTest("""
        struct Foo;
        struct Bar<T>(T);
        struct Baz<T> { x: T }
        trait Trait/*caret*/ {
            fn bar();
        }

        impl Trait for Foo {
            fn bar() { todo!() }
        }
        impl<T> Trait for Bar<T> {
            fn bar() { todo!() }
        }
        impl<T> Trait for Baz<T> where T : Clone {
            fn bar() { todo!() }
        }
    """,
        "Trait for Foo test_package",
        "Trait for Bar<T> test_package",
        "Trait for Baz<T> where T: Clone test_package"
    )

    private fun doSingleTargetTest(@Language("Rust") before: String, @Language("Rust") after: String) =
        checkEditorAction(before, after, IdeActions.ACTION_GOTO_IMPLEMENTATION)

    private fun doMultipleTargetsTest(@Language("Rust") before: String, vararg expected: String) {
        InlineFile(before).withCaret()
        val data = CodeInsightTestUtil.gotoImplementation(myFixture.editor, myFixture.file)
        val actual = data.targets.map { data.getComparingObject(it) }
        assertEquals(expected.toList(), actual)
    }
}
