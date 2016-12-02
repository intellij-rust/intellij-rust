package org.rust.ide.intentions

class ImplementStructIntentionTest : RustIntentionTestBase(ImplementStructIntention()) {
    fun testImplementStruct() = doAvailableTest(
        """
struct Hey/*caret*/ {
    var: i32
}
""", """
struct Hey {
    var: i32
}

impl Hey {/*caret*/}
"""
    )
}
