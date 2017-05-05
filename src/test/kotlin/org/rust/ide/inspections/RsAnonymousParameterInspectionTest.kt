package org.rust.ide.inspections

class RsAnonymousParameterInspectionTest : RsInspectionsTestBase(RsAnonymousParameterInspection()) {
    fun `test fix`() = checkFixByText("Add dummy parameter name", """
        trait Display {
            fn fmt(&self, <warning descr="Anonymous functions parameters are deprecated (RFC 1685)">F<caret></warning>);
        }
    """, """
        trait Display {
            fn fmt(&self, _: F);
        }
    """)
}
