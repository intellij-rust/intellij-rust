package org.rust.ide.intentions

class NavigateToImplementationTest : RustIntentionTestBase(NavigateToImplementationIntention()) {
    fun testNavigateToImplementation() = doAvailableTest(
        """
struct /*caret*/MyTest {

}

impl MyTest {

}
        """, """
struct MyTest {

}

/*caret*/impl MyTest {

}
        """
    )
}
