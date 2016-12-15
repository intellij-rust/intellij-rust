package org.rust.ide.intentions

class NavigateToImplementationIntentionTest : RustIntentionTestBase(NavigateToImplementationIntention()) {
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
