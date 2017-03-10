package org.rust.ide.template.postfix

class NotPostfixTemplateTest : PostfixTemplateTest(NotPostfixTemplate()) {
    fun `test simple`() = doTest("""
        fn foo() {
            assert!(nodes.is_empty().not/*caret*/);
        }
    """, """
        fn foo() {
            assert!(!nodes.is_empty()/*caret*/);
        }
    """)
}
