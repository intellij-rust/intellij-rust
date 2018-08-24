/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.MockRustcVersion
import org.rust.ide.annotator.RsAnnotationTestBase

class AddFeatureAttributeFixTest : RsAnnotationTestBase() {

    @MockRustcVersion("1.27.1")
    fun `test add crate_visibility_modifier feature is unavailable`() = checkFixIsUnavailable(
        "Add `crate_visibility_modifier` feature", """
        <error>crate/*caret*/</error> struct Foo;
    """)

    @MockRustcVersion("1.29.0-nightly")
    fun `test add crate_visibility_modifier feature`() = checkFixByText("Add `crate_visibility_modifier` feature", """
        <error>crate/*caret*/</error> struct Foo;
    """, """
        #![feature(crate_visibility_modifier)]

        crate/*caret*/ struct Foo;
    """)

    @MockRustcVersion("1.29.0-nightly")
    fun `test add crate_visibility_modifier feature after all feature attributes`() =
        checkFixByText("Add `crate_visibility_modifier` feature", """
            #![feature(i128_type)]

            <error>crate/*caret*/</error> type Foo = i128;
        """, """
            #![feature(i128_type)]
            #![feature(crate_visibility_modifier)]

            crate/*caret*/ type Foo = i128;
        """)
}
