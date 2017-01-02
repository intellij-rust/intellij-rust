package org.rust.ide.intentions

class AddCurlyBracesIntentionTest : RustIntentionTestBase(AddCurlyBracesIntention()) {

    fun testAddCurlyBracesSimple() = doAvailableTest(
        "use std::m/*caret*/em;",
        "use std::{m/*caret*/em};"
    )

    fun testAddCurlyBracesLonger() = doAvailableTest(
        "use foo::bar::/*caret*/baz::qux;",
        "use foo::bar::/*caret*/baz::{qux};"
    )

    fun testAddCurlyBracesAlias() = doAvailableTest(
        "use std::mem as mem/*caret*/ory;",
        "use std::{mem as mem/*caret*/ory};"
    )

    fun testAddCurlyBracesExtra() = doAvailableTest(
        "#[macro_use] pub use /*comment*/ std::me/*caret*/m;",
        "#[macro_use] pub use /*comment*/ std::{me/*caret*/m};"
    )
}
