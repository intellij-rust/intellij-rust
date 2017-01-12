package org.rust.ide.intentions

class AddDeriveIntentionTest : RsIntentionTestBase(AddDeriveIntention()) {

    fun testAddDeriveStruct() = doAvailableTest("""
        struct Te/*caret*/st {}
    """, """
        #[derive(/*caret*/)]
        struct Test {}
    """)

    fun testAddDerivePubStruct() = doAvailableTest("""
        pub struct Te/*caret*/st {}
    """, """
        #[derive(/*caret*/)]
        pub struct Test {}
    """)

    // FIXME: there is something weird with enum re-formatting, for some reason it adds more indentation
    fun testAddDeriveEnum() = doAvailableTest("""
        enum Test /*caret*/{
            Something
        }
    """, """
        #[derive(/*caret*/)]
        enum Test {
    Something
}
    """)

    fun testAddDeriveExistingAttr() = doAvailableTest("""
        #[derive(Something)]
        struct Test/*caret*/ {}
    """, """
        #[derive(Something/*caret*/)]
struct Test {}
    """)
}
