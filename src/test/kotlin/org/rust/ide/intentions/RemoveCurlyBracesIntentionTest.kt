/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class RemoveCurlyBracesIntentionTest : RsIntentionTestBase(RemoveCurlyBracesIntention()) {

    fun `test remove curly braces simple`() = doAvailableTest(
        "use std::{m/*caret*/em};",
        "use std::m/*caret*/em;"
    )

    fun `test remove curly braces longer`() = doAvailableTest(
        "use foo::bar::/*caret*/baz::{qux};",
        "use foo::bar::/*caret*/baz::qux;"
    )

// TODO: ideally this should work
//    fun `test remove curly braces alias`() = doAvailableTest(
//        "use std::{mem as mem/*caret*/ory};",
//        "use std::mem as mem/*caret*/ory;"
//    )

    fun `test remove curly braces extra`() = doAvailableTest(
        "#[macro_use] pub use /*comment*/ std::{me/*caret*/m};",
        "#[macro_use] pub use /*comment*/ std::me/*caret*/m;"
    )
}
