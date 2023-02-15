/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.macros.MacroExpansionScope
import java.util.*
import java.util.concurrent.Callable

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

    @ExpandMacros(MacroExpansionScope.WORKSPACE)
    @WithExperimentalFeatures(RsExperiments.PROC_MACROS)
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun `test trait under a proc macro attribute`() = doSingleTargetTest("""
        use test_proc_macros::attr_as_is;
        #[attr_as_is]
        trait T/*caret*/{
            fn test(&self);
        }
        /// docs
        impl T for (){
            fn test(&self) {}
        }
    """, """
        use test_proc_macros::attr_as_is;
        #[attr_as_is]
        trait T{
            fn test(&self);
        }
        /// docs
        impl T for /*caret*/(){
            fn test(&self) {}
        }
    """)

    @ExpandMacros(MacroExpansionScope.WORKSPACE)
    @WithExperimentalFeatures(RsExperiments.PROC_MACROS)
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun `test impl under a proc macro attribute`() = doSingleTargetTest("""
        use test_proc_macros::attr_as_is;
        trait T/*caret*/{
            fn test(&self);
        }
        /// docs
        #[attr_as_is]
        impl T for (){
            fn test(&self) {}
        }
    """, """
        use test_proc_macros::attr_as_is;
        trait T{
            fn test(&self);
        }
        /// docs
        #[attr_as_is]
        impl T for /*caret*/(){
            fn test(&self) {}
        }
    """)

    @ExpandMacros(MacroExpansionScope.WORKSPACE)
    @WithExperimentalFeatures(RsExperiments.PROC_MACROS)
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun `test member when trait is under a proc macro attribute`() = doSingleTargetTest("""
        use test_proc_macros::attr_as_is;
        #[attr_as_is]
        trait T{
            fn test/*caret*/(&self);
        }
        impl T for (){
            fn test(&self) {}
        }
    """, """
        use test_proc_macros::attr_as_is;
        #[attr_as_is]
        trait T{
            fn test(&self);
        }
        impl T for (){
            fn /*caret*/test(&self) {}
        }
    """)

    @ExpandMacros(MacroExpansionScope.WORKSPACE)
    @WithExperimentalFeatures(RsExperiments.PROC_MACROS)
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun `test struct under a proc macro attribute`() = doSingleTargetTest("""
        use test_proc_macros::attr_as_is;
        trait T {
            fn test(&self);
        }
        #[attr_as_is]
        struct Foo/*caret*/;
        /// docs
        impl T for Foo {
            fn test(&self) {}
        }
    """, """
        use test_proc_macros::attr_as_is;
        trait T {
            fn test(&self);
        }
        #[attr_as_is]
        struct Foo;
        /// docs
        impl T for /*caret*/Foo {
            fn test(&self) {}
        }
    """)

    private fun doSingleTargetTest(@Language("Rust") before: String, @Language("Rust") after: String) =
        checkEditorAction(before, after, IdeActions.ACTION_GOTO_IMPLEMENTATION)

    private fun doMultipleTargetsTest(@Language("Rust") before: String, vararg expected: String) {
        InlineFile(before).withCaret()
        val actual = doGoToImplementation()
        assertEquals(expected.toList(), actual)
    }

    private fun doGoToImplementation(): List<String> {
        val data = CodeInsightTestUtil.gotoImplementation(myFixture.editor, myFixture.file)

        val future = ApplicationManager.getApplication().executeOnPooledThread(Callable {
            @Suppress("UnstableApiUsage")
            runReadAction {
                data.targets
                    .map { GotoTargetHandler.computePresentation(it, data.hasDifferentNames()) }
                    // Copied from `com.intellij.codeInsight.navigation.GotoTargetHandler.GotoData.getComparingObject`
                    .map { listOfNotNull(it.presentableText, it.containerText, it.locationText).joinToString(" ") }
            }
        })
        return ProgressIndicatorUtils.awaitWithCheckCanceled(future)
    }
}
