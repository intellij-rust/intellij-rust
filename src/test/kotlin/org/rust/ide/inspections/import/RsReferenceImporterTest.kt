/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.import

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.intellij.lang.annotations.Language
import org.rust.SkipTestWrapping
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsUnresolvedReferenceInspection
import org.rust.ide.settings.RsCodeInsightSettings

@SkipTestWrapping // TODO Support macros in RsReferenceImporter
class RsReferenceImporterTest : RsInspectionsTestBase(RsUnresolvedReferenceInspection::class) {

    fun `test single item`() = doTest("""
        fn main() {
            func();/*caret*/
        }
        mod inner {
            pub fn func() {}
        }
    """, """
        use crate::inner::func;

        fn main() {
            func();
        }
        mod inner {
            pub fn func() {}
        }
    """)

    fun `test single method`() = doTest("""
        mod inner {
            pub trait Trait {
                fn method(&self) {}
            }
            impl Trait for crate::Foo {}
        }

        struct Foo {}
        fn func(foo: Foo) {
            foo.method();
        }
    """, """
        use crate::inner::Trait;

        mod inner {
            pub trait Trait {
                fn method(&self) {}
            }
            impl Trait for crate::Foo {}
        }

        struct Foo {}
        fn func(foo: Foo) {
            foo.method();
        }
    """)

    fun `test multiple items`() = doTestNotChanged("""
        fn main() {
            func();/*caret*/
        }
        mod mod1 {
            pub fn func() {}
        }
        mod mod2 {
            pub fn func() {}
        }
    """)

    fun `test option is disabled`() = doTestNotChanged("""
        fn main() {
            func();/*caret*/
        }
        mod mod1 {
            pub fn func() {}
        }
        mod mod2 {
            pub fn func() {}
        }
    """, enabled = false)

    private fun doTestNotChanged(@Language("Rust") code: String, enabled: Boolean = true) = doTest(code, code, enabled)

    private fun doTest(@Language("Rust") before: String, @Language("Rust") after: String, enabled: Boolean = true) {
        withAddUnambiguousImportsOnTheFly(enabled) {
            configureByText(before)
            // For some reason have to call highlighting twice
            myFixture.doHighlightingAndAllowModifications()
            myFixture.doHighlightingAndAllowModifications()
            annotationFixture.checkByText(after)
        }
    }

    private fun withAddUnambiguousImportsOnTheFly(value: Boolean, action: () -> Unit) {
        val settings = RsCodeInsightSettings.getInstance()
        val oldValue = settings.addUnambiguousImportsOnTheFly
        settings.addUnambiguousImportsOnTheFly = value
        try {
            action()
        } finally {
            settings.addUnambiguousImportsOnTheFly = oldValue
        }
    }
}

/** Same as [CodeInsightTestFixture.doHighlighting] but allows changing PSI during highlighting */
private fun CodeInsightTestFixture.doHighlightingAndAllowModifications() {
    CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, intArrayOf(), /* canChangeDocument */ true)
}
