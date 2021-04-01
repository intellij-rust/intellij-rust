/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language

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

    fun `test impls sorting`() = doPopupTest("""
        trait Bar {}
        trait Foo {}
        struct FooBar/*caret*/;

        impl Foo for FooBar {}
        impl Bar for FooBar {}
    """,
        "Bar for FooBar",
        "Foo for FooBar"
    )

    private fun doPopupTest(@Language("Rust") code: String, vararg expectedItems: String) {
        InlineFile(code)
        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!

        @Suppress("UNCHECKED_CAST")
        val markerInfo = (myFixture.findGuttersAtCaret().first() as LineMarkerInfo.LineMarkerGutterIconRenderer<PsiElement>).lineMarkerInfo
        markerInfo.invokeNavigationHandler(element)
        val renderedImpls = element.getUserData(RsImplsLineMarkerProvider.RENDERED_IMPLS)!!
        assertEquals(expectedItems.toList(), renderedImpls)
    }
}
