package org.rust.ide.intentions

class AddTurbofishIntentionTest : RsIntentionTestBase(AddTurbofishIntention()) {

    fun testAddTurbofishBase() = doAvailableTest(
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

    fun testAddTurbofishShouldBeAvailableAlsoInRightSide() = doAvailableTest(
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

    fun _testAddTurbofishShouldWorkAlsoWithMoreThanOneGenericArguments() = doAvailableTest(
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

    fun testAddTurbofishShouldNotBeAvailableIfTheInstanceIsCorrectedYet() = doUnavailableTest(
        """
        fn foo() {
            let _ = parse::</*caret*/i32>("42");
        }
        """)

    fun testAddTurbofishShouldNotTriggerMisspelledComparisonSentences() {
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

    fun testAddTurbofishShouldBeAvailableJustIfLooksLikeAGenericReference() {
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

