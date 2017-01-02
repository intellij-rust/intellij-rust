package org.rust.ide.intentions

class RemoveCurlyBracesIntentionTest : RustIntentionTestBase(RemoveCurlyBracesIntention()) {

    fun testRemoveCurlyBracesSimple() = doAvailableTest(
        "use std::{m/*caret*/em};",
        "use std::m/*caret*/em;"
    )

    fun testRemoveCurlyBracesLonger() = doAvailableTest(
        "use foo::bar::/*caret*/baz::{qux};",
        "use foo::bar::/*caret*/baz::qux;"
    )

    fun testRemoveCurlyBracesAlias() = doAvailableTest(
        "use std::{mem as mem/*caret*/ory};",
        "use std::mem as mem/*caret*/ory;"
    )

    fun testRemoveCurlyBracesExtra() = doAvailableTest(
        "#[macro_use] pub use /*comment*/ std::{me/*caret*/m};",
        "#[macro_use] pub use /*comment*/ std::me/*caret*/m;"
    )
}
