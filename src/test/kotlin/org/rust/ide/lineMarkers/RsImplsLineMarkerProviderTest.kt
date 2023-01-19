/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.ExpandMacros
import org.rust.ProjectDescriptor
import org.rust.WithExperimentalFeatures
import org.rust.WithProcMacroRustProjectDescriptor
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.macros.MacroExpansionScope

class RsImplsLineMarkerProviderTest : RsLineMarkerProviderTestBase() {

    fun `test no impls`() = doTestByText("""
        // ideally don't want a marker here, but that's costly!
        trait Foo {} // - Has implementations
    """)

    fun `test one impl`() = doTestByText("""
        trait Foo {}  // - Has implementations
        struct Bar {} // - Has implementations
        impl Foo for Bar {}
    """)

    fun `test multiple impls`() = doTestByText("""
        trait Foo {}  // - Has implementations
        mod bar {
            use super::Foo;
            struct Bar {} // - Has implementations
            impl Foo for Bar {}
        }
        mod baz {
            use super::Foo;
            struct Baz {}  // - Has implementations
            impl Foo for Baz {}
        }
    """)

    fun `test icon position`() = doTestByText("""
        ///
        /// Documentation
        ///
        #[warn(non_camel_case_types)]
        trait
        Foo // - Has implementations
        {}
        struct
        Bar // - Has implementations
        {}
        impl Foo for Bar {}
    """)

    fun `test negative impls`() = doPopupTest("""
        trait Foo {}
        struct Bar/*caret*/;
        impl !Foo for Bar {}
    """, "!Foo for Bar")

    fun `test impls sorting`() = doPopupTest("""
        trait Bar {}
        trait Foo {}
        trait Baz {}
        struct FooBar/*caret*/;

        impl Foo for FooBar {}
        impl Bar for FooBar {}
        impl !Baz for FooBar {}
    """,
        "!Baz for FooBar",
        "Bar for FooBar",
        "Foo for FooBar"
    )

    @ExpandMacros(MacroExpansionScope.WORKSPACE)
    @WithExperimentalFeatures(RsExperiments.PROC_MACROS)
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun `test struct an trait under a proc macro attribute`() = doTestByText("""
        use test_proc_macros::attr_as_is;
        #[attr_as_is]
        trait Foo {}  // - Has implementations
        #[attr_as_is]
        struct Bar {} // - Has implementations
        impl Foo for Bar {}
    """)

    private fun doPopupTest(@Language("Rust") code: String, vararg expectedItems: String) {
        InlineFile(code)
        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!

        @Suppress("UNCHECKED_CAST")
        val markerInfo = (myFixture.findGuttersAtCaret().first() as LineMarkerInfo.LineMarkerGutterIconRenderer<PsiElement>).lineMarkerInfo
        val renderedImpls = markerInfo.invokeNavigationHandler(element, RsImplsLineMarkerProvider.RENDERED_IMPLS)!!
        assertEquals(expectedItems.toList(), renderedImpls)
    }
}
