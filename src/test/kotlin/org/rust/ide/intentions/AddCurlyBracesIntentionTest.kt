/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class AddCurlyBracesIntentionTest : RsIntentionTestBase(AddCurlyBracesIntention()) {

    fun `test add curly braces simple`() = doAvailableTest(
        "use std::m/*caret*/em;",
        "use std::{m/*caret*/em};"
    )

    fun `test add curly brace super`() = doAvailableTest(
        "use super/*caret*/::qux;",
        "use super::{qux};"
    )

    fun `test add curly braces longer`() = doAvailableTest(
        "use foo::bar::/*caret*/baz::qux;",
        "use foo::bar::/*caret*/baz::{qux};"
    )

    fun `test add curly braces alias`() = doAvailableTest(
        "use std::mem as mem/*caret*/ory;",
        "use std::{mem as mem/*caret*/ory};"
    )

    fun `test add curly braces extra`() = doAvailableTest(
        "#[macro_use] pub use /*comment*/ std::me/*caret*/m;",
        "#[macro_use] pub use /*comment*/ std::{me/*caret*/m};"
    )

    fun `test not available for star imports`() = doUnavailableTest("""
        use foo::*/*caret*/;
    """)
}
