/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class RemoveCurlyBracesIntentionTest : RsIntentionTestBase(RemoveCurlyBracesIntention::class) {
    fun `test unavailable on groups with more than one element`() = doUnavailableTest(
        "use std::{f/*caret*/oo, bar};"
    )

    fun `test unavailable on speck without a group`() = doUnavailableTest(
        "use std::f/*caret*/oo;"
    )

    fun `test remove curly braces simple`() = doAvailableTest(
        "use std::{m/*caret*/em};",
        "use std::m/*caret*/em;"
    )

    fun `test remove curly braces longer`() = doAvailableTest(
        "use foo::bar::/*caret*/baz::{qux};",
        "use foo::bar::/*caret*/baz::qux;"
    )

    fun `test remove curly braces alias`() = doAvailableTest(
        "use std::{mem as mem/*caret*/ory};",
        "use std::mem as mem/*caret*/ory;"
    )

    fun `test remove curly braces extra`() = doAvailableTest(
        "#[macro_use] pub use /*comment*/ std::{me/*caret*/m};",
        "#[macro_use] pub use /*comment*/ std::me/*caret*/m;"
    )

    fun `test nested`() = doAvailableTest(
        "use foo::{bar::{baz/*caret*/}};",
        "use foo::{bar::baz/*caret*/};",
    )

    fun `test qualified path`() = doAvailableTest(
        "use foo::{bar::baz/*caret*/};",
        "use foo::bar::baz/*caret*/;",
    )
}
