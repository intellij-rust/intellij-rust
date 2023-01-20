/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.suggested

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.util.ThrowableRunnable
import org.rust.ide.disableFindUsageTests

class RsRenameSuggestedRefactoringTest : RsSuggestedRefactoringTestBase() {

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        if (!disableFindUsageTests) {
            super.runTestRunnable(testRunnable)
        }
    }

    fun `test rename local variable`() = doTestRename("""
        fn foo() {
            let a/*caret*/ = 0;
            let b = a;
        }
    """, """
        fn foo() {
            let ax/*caret*/ = 0;
            let b = ax;
        }
    """, "a", "ax"
    ) { myFixture.type("x") }

    fun `test rename add number`() = doTestRename("""
        fn foo() {
            let a/*caret*/ = 0;
            let b = a;
        }
    """, """
        fn foo() {
            let a5/*caret*/ = 0;
            let b = a5;
        }
    """, "a", "a5"
    ) { myFixture.type("5") }

    fun `test delete and rename`() = doTestRename("""
        fn foo() {
            let /*caret*/a = 0;
            let b = a;
        }
    """, """
        fn foo() {
            let x/*caret*/ = 0;
            let b = x;
        }
    """, "a", "x"
    ) {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE)
        myFixture.type("x")
    }

    fun `test delete and use invalid name`() = doUnavailableTest("""
        fn foo() {
            let /*caret*/a = 0;
            let b = a;
        }
    """) {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE)
        myFixture.type("5")
    }

    fun `test do not rename constant`() = doUnavailableTest("""
        const C: i32 = 1;
        fn foo() {
            match 1 {
                C/*caret*/ => println!("Oops"),
                X => println!("{}", X)
            };
        }
    """) {
        myFixture.type("1")
    }

    fun `test rename nested binding`() = doTestRename("""
        fn foo() {
            let (a/*caret*/, b) = (0, 0);
            let b = a;
        }
    """, """
        fn foo() {
            let (ax/*caret*/, b) = (0, 0);
            let b = ax;
        }
    """, "a", "ax"
    ) { myFixture.type("x") }

    fun `test rename function`() = doTestRename("""
        fn foo/*caret*/() {
            let a = 5;
        }
        fn baz<T>(t: T) {}
        fn bar() {
            foo();
            baz(foo);
        }
    """, """
        fn foox/*caret*/() {
            let a = 5;
        }
        fn baz<T>(t: T) {}
        fn bar() {
            foox();
            baz(foox);
        }
    """, "foo", "foox"
    ) { myFixture.type("x") }

    fun `test rename struct`() = doTestRename("""
        struct S/*caret*/;
        fn bar(s: S) {}
    """, """
        struct Sx/*caret*/;
        fn bar(s: Sx) {}
    """, "S", "Sx"
    ) { myFixture.type("x") }

    fun `test rename struct field`() = doTestRename("""
        struct S {
            a/*caret*/: u32
        }
        fn bar(s: S) {
            let x = s.a;
        }
    """, """
        struct S {
            ax/*caret*/: u32
        }
        fn bar(s: S) {
            let x = s.ax;
        }
    """, "a", "ax"
    ) { myFixture.type("x") }

    fun `test rename trait`() = doTestRename("""
        trait Trait/*caret*/ {}
        fn bar<T: Trait>(t: T) {}
    """, """
        trait Traitx/*caret*/ {}
        fn bar<T: Traitx>(t: T) {}
    """, "Trait", "Traitx"
    ) { myFixture.type("x") }

    fun `test rename macro`() = doTestRename("""
        macro_rules! foo/*caret*/ {
            () => {}
        }
        fn bar() {
            foo!();
        }
    """, """
        macro_rules! foox/*caret*/ {
            () => {}
        }
        fn bar() {
            foox!();
        }
    """, "foo", "foox"
    ) { myFixture.type("x") }
}
