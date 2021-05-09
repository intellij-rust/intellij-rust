/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import org.rust.ide.inspections.RsInspectionsTestBase

class RsNonShorthandFieldPatternsInspectionTest : RsInspectionsTestBase(RsNonShorthandFieldPatternsInspection::class) {
    fun `test not applicable`() = checkFixIsUnavailable("Use shorthand field pattern: `foo`", """
        fn main() {
            match foo {
                S { foo: bar/*caret*/, baz: &baz } => (),
            }
        }
    """, checkWeakWarn = true)

    fun `test allow non_shorthand_field_patterns`() = checkWarnings("""
        #[allow(non_shorthand_field_patterns)]
        fn main() {
            match foo {
                S { foo: foo/*caret*/, baz: quux } => (),
            }
        }
    """)

    fun `test deny non_shorthand_field_patterns`() = checkWarnings("""
        #[deny(non_shorthand_field_patterns)]
        fn main() {
            match foo {
                S { <error descr="The `foo:` in this pattern is redundant">foo: foo/*caret*/</error>, baz: quux } => (),
            }
        }
    """)

    fun `test fix`() = checkFixByText("Use shorthand field pattern: `foo`", """
        fn main() {
            match foo {
                S { <weak_warning descr="The `foo:` in this pattern is redundant">foo: foo/*caret*/</weak_warning>, baz: quux } => (),
            }
        }
    """, """
        fn main() {
            match foo {
                S { foo/*caret*/, baz: quux } => (),
            }
        }
    """, checkWeakWarn = true)
}
