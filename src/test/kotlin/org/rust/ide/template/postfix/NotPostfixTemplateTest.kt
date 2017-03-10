package org.rust.ide.template.postfix

class NotPostfixTemplateTest : PostfixTemplateTest(NotPostfixTemplate()) {
    fun `test simple`() = doTest("""
        fn foo() {
            assert!(self.nodes.is_empty().not/*caret*/);
        }
    """, """
        fn foo() {
            assert!(!self.nodes.is_empty()/*caret*/);
        }
    """)
}
