package org.rust.ide.annotator.fixes

import org.junit.Test
import org.rust.ide.annotator.RsAnnotatorTestBase

class AddSelfTest : RsAnnotatorTestBase() {
    @Test
    fun testNoParameters() = checkQuickFix("Add self to function", """
struct S;

impl S {
    fn foo() {
        self/*caret*/;
    }
}
""", """
struct S;

impl S {
    fn foo(&self) {
        self/*caret*/;
    }
}
""")

    @Test
    fun testHasParameters() = checkQuickFix("Add self to function", """
struct S;

impl S {
    fn foo(a: i32) {
        self/*caret*/;
    }
}
""", """
struct S;

impl S {
    fn foo(&self, a: i32) {
        self/*caret*/;
    }
}
""")
}
