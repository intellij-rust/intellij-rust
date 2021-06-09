/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import org.rust.ide.inspections.RsInspectionsTestBase

class RsUnknownCrateTypesInspectionTest : RsInspectionsTestBase(RsUnknownCrateTypesInspection::class) {
    fun `test incorrect crate type`() = checkWarnings("""
        #![crate_type = <error descr="Invalid `crate_type` value">"foo"</error>]
    """)

    fun `test correct crate type`() = checkWarnings("""
        #![crate_type = "cdylib"]
    """)

    fun `test ignore if outer`() = checkWarnings("""
        #[crate_type = "foo"]
        fn main() {}
    """)

    fun `test ignore if not root`() = checkWarnings("""
        #![bar(crate_type = "foo")]
    """)

    fun `test warn unknown_crate_types`() = checkWarnings("""
        #![warn(unknown_crate_types)]
        #![crate_type = <warning descr="Invalid `crate_type` value">"foo"</warning>]
    """)

    fun `test allow unknown_crate_types`() = checkWarnings("""
        #![allow(unknown_crate_types)]
        #![crate_type = "foo"]
    """)
}
