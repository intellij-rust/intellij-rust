/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.intellij.lang.annotations.Language
import org.rust.ide.inspections.import.checkAutoImportWithMultipleChoice

class AddImportForPatternIntentionTest : RsIntentionTestBase(AddImportForPatternIntention::class) {

    fun `test constant`() = doAvailableTest("""
        mod inner {
            pub const C: i32 = 0;
        }

        fn main() {
            match 0 {
                /*caret*/C => {}
                _ => {}
            }
        }
    """, """
        use crate::inner::C;

        mod inner {
            pub const C: i32 = 0;
        }

        fn main() {
            match 0 {
                C => {}
                _ => {}
            }
        }
    """)

    fun `test enum variant`() = doAvailableTest("""
        enum E { A, B }
        fn func(e: E) {
            match e {
                /*caret*/A => {}
                B => {}
            }
        }
    """, """
        use crate::E::A;

        enum E { A, B }
        fn func(e: E) {
            match e {
                A => {}
                B => {}
            }
        }
    """)

    fun `test complex pattern`() = doAvailableTest("""
        mod inner {
            pub const C: i32 = 0;
        }

        fn main() {
            match (0, 0) {
                (/*caret*/C, _) => {}
            }
        }
    """, """
        use crate::inner::C;

        mod inner {
            pub const C: i32 = 0;
        }

        fn main() {
            match (0, 0) {
                (C, _) => {}
            }
        }
    """)

    // Ideally we should filter variants by expected type
    fun `test enum variant and constant`() = checkAutoImportVariantsByText("""
        mod inner {
            pub const C: i32 = 0;
        }
        enum E { C }
        fn func(e: E) {
            match e {
                /*caret*/C => {}
                _ => {}
            }
        }
    """, listOf("crate::E::C", "crate::inner::C"))

    fun `test unavailable for ref pattern`() = doUnavailableTest("""
        mod inner {
            pub const C: i32 = 0;
        }

        fn main() {
            match 0 {
                ref /*caret*/C => {}
                _ => {}
            }
        }
    """)

    // Potentially intention can work in this case
    fun `test unavailable for field binding`() = doUnavailableTest("""
        mod inner {
            pub const C: i32 = 0;
        }

        struct Foo { C: i32 }
        fn func(foo: Foo) {
            match foo {
                Foo { /*caret*/C } => {}
            }
        }
    """)

    fun `test unavailable for let declaration`() = doUnavailableTest("""
        mod inner {
            pub const C: i32 = 0;
        }

        fn func(foo: Foo) {
            let /*caret*/C = 1;
        }
    """)

    fun `test unavailable for resolved path`() = doUnavailableTest("""
        mod inner {
            pub const C: i32 = 0;
        }

        pub const C: i32 = 0;
        fn main() {
            match 0 {
                /*caret*/C => {}
                _ => {}
            }
        }
    """)

    private fun checkAutoImportVariantsByText(
        @Language("Rust") before: String,
        expectedElements: List<String>
    ) = checkAutoImportWithMultipleChoice(expectedElements, choice = null) {
        InlineFile(before.trimIndent()).withCaret()
        val previewChecker = launchAction()
        testWrappingUnwrapper?.unwrap()
        myFixture.checkResult(replaceCaretMarker(before.trimIndent()))
        previewChecker.checkPreview()
    }
}
