package org.rust.ide.intentions

class AddNamespaceIntentionTest : RsIntentionTestBase(AddNamespaceIntention()) {

    fun testAddNamespaceBase() = doAvailableTest(
        """
        fn foo() {
            let _ = parse/*caret*/<i32>("42");
        }
        """,
        """
        fn foo() {
            let _ = parse::<i32>("42");
        }
        """)

    fun testAddNamespaceShouldBeAvailableAlsoInRightSide() = doAvailableTest(
        """
        fn foo() {
            let _ = parse<i32>(/*caret*/"42");
        }
        """,
        """
        fn foo() {
            let _ = parse::<i32>("42");
        }
        """)

    fun _testAddNamespaceShouldWorkAlsoWithMoreThanOneGenericArguments() = doAvailableTest(
        """
        fn foo() {
            let _ = some<i32, f32>(/*caret*/"42");
        }
        """,
        """
        fn foo() {
            let _ = some::<i32, f32>(/*caret*/"42");
        }
        """)

    fun testAddNamespaceShouldNotBeAvailableIfTheIstanceIsCorrecteYet() = doUnavailableTest(
        """
        fn foo() {
            let _ = parse::</*caret*/i32>("42");
        }
        """)

    fun testAddNamespaceShouldNotTriggerMisspelledComparisonSentences() {
        doUnavailableTest(
            """
            fn foo(x: i32) {
                let _ = 1 < /*caret*/x < 5;
            }
            """)
        doUnavailableTest(
            """
            fn foo(x: i32, y: i32) {
                let _ = 1 < /*caret*/x > 5;
            }
            """)
    }

    fun testAddNamespaceShouldBeAilableJustIfLooksLikeAGenericReference() {
        doUnavailableTest(
            """
            fn foo(x: i32) {
                let _ = parse>/*caret*/i32>("42");
            }
            """)
        doUnavailableTest(
            """
            fn foo(x: i32) {
                let _ = parse</*caret*/i32<("42");
            }
            """)
    }
}

