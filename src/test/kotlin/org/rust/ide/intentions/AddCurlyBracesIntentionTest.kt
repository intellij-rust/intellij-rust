/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class AddCurlyBracesIntentionTest : RsIntentionTestBase(AddCurlyBracesIntention()) {

    fun `test add curly braces simple`() = doAvailableTest(
        "use std::m/*caret*/em;",
        "use std::{mem/*caret*/};"
    )

    fun `test add curly brace super`() = doAvailableTest(
        "use super/*caret*/::qux;",
        "use super::{qux};"
    )

    fun `test add curly braces longer`() = doAvailableTest(
        "use foo::bar::/*caret*/baz::qux;",
        "use foo::bar::baz::{qux/*caret*/};"
    )

    fun `test add curly braces alias`() = doAvailableTest(
        "use std::mem as mem/*caret*/ory;",
        "use std::{mem as memory/*caret*/};"
    )

    fun `test add curly braces extra`() = doAvailableTest(
        "#[macro_use] pub use /*comment*/ std::me/*caret*/m;",
        "#[macro_use] pub use /*comment*/ std::{mem/*caret*/};"
    )

    fun `test works for root path 1`() = doAvailableTest(
        "use ::foo/*caret*/;",
        "use ::{foo/*caret*/};"
    )

    fun `test works for root path 2`() = doAvailableTest(
        "use foo/*caret*/;",
        "use ::{foo/*caret*/};"
    )

    fun `test not available for star imports`() = doUnavailableTest("""
        use foo::*/*caret*/;
    """)
}
