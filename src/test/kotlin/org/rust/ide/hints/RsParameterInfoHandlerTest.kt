/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext
import com.intellij.testFramework.utils.parameterInfo.MockParameterInfoUIContext
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext
import junit.framework.AssertionFailedError
import junit.framework.TestCase
import org.rust.lang.RsTestBase


/**
 * Tests for RustParameterInfoHandler
 */
class RsParameterInfoHandlerTest : RsTestBase() {
    fun testFnNoArgs() = checkByText("""
        fn foo() {}
        fn main() { foo(<caret>); }
    """, "<no arguments>", -1)

    fun testFnNoArgsBeforeArgs() = checkByText("""
        fn foo() {}
        fn main() { foo<caret>(); }
    """, "<no arguments>", -1)

    fun testFnOneArg() = checkByText("""
        fn foo(arg: u32) {}
        fn main() { foo(<caret>); }
    """, "arg: u32", 0)

    fun `test struct one arg`() = checkByText("""
        struct Foo(u32);
        fn main() { Foo(<caret>); }
    """, "_: u32", 0)

    fun `test enum one arg`() = checkByText("""
        enum E  { Foo(u32) }
        fn main() { E::Foo(<caret>); }
    """, "_: u32", 0)

    fun testFnOneArgEnd() = checkByText("""
        fn foo(arg: u32) {}
        fn main() { foo(42<caret>); }
    """, "arg: u32", 0)

    fun testFnManyArgs() = checkByText("""
        fn foo(id: u32, name: &'static str, mut year: &u16) {}
        fn main() { foo(<caret>); }
    """, "id: u32, name: &'static str, mut year: &u16", 0)

    fun testFnPoorlyFormattedArgs() = checkByText("""
        fn foo(  id   :   u32   , name: &'static str   , mut year   : &u16   ) {}
        fn main() { foo(<caret>); }
    """, "id: u32, name: &'static str, mut year: &u16", 0)

    fun testFnArgIndex0() = checkByText("""
        fn foo(a1: u32, a2: u32) {}
        fn main() { foo(a1<caret>); }
    """, "a1: u32, a2: u32", 0)

    fun testFnArgIndex0WithComma() = checkByText("""
        fn foo(a1: u32, a2: u32) {}
        fn main() { foo(a1<caret>,); }
    """, "a1: u32, a2: u32", 0)

    fun testFnArgIndex1() = checkByText("""
        fn foo(a1: u32, a2: u32) {}
        fn main() { foo(16,<caret>); }
    """, "a1: u32, a2: u32", 1)

    fun testFnArgIndex1ValueStart() = checkByText("""
        fn foo(a1: u32, a2: u32) {}
        fn main() { foo(12, <caret>32); }
    """, "a1: u32, a2: u32", 1)

    fun testFnArgIndex1ValueEnd() = checkByText("""
        fn foo(a1: u32, a2: u32) {}
        fn main() { foo(5, 32<caret>); }
    """, "a1: u32, a2: u32", 1)

    fun testFnArgTooManyArgs() = checkByText("""
        fn foo(a1: u32, a2: u32) {}
        fn main() { foo(0, 32,<caret>); }
    """, "a1: u32, a2: u32", -1)

    fun testFnClosure() = checkByText("""
        fn foo(fun: Fn(u32) -> u32) {}
        fn main() { foo(|x| x + <caret>); }
    """, "fun: Fn(u32) -> u32", 0)

    fun testFnNestedInner() = checkByText("""
        fn add(v1: u32, v2: u32) -> u32 { v1 + v2 }
        fn display(v: u32, format: &'static str) {}
        fn main() { display(add(4, <caret>), "0.00"); }
    """, "v1: u32, v2: u32", 1)

    fun testFnNestedOuter() = checkByText("""
        fn add(v1: u32, v2: u32) -> u32 { v1 + v2 }
        fn display(v: u32, indent: bool, format: &'static str) {}
        fn main() { display(add(4, 7), false, <caret>"); }
    """, "v: u32, indent: bool, format: &'static str", 2)

    fun testMultiline() = checkByText("""
        fn sum(v1: u32, v2: u32, v3: u32) -> u32 { v1 + v2 + v3 }
        fn main() {
            sum(
                10,
                <caret>
            );
        }
    """, "v1: u32, v2: u32, v3: u32", 1)

    fun testAssocFn() = checkByText("""
        struct Foo;
        impl Foo { fn new(id: u32, val: f64) {} }
        fn main() {
            Foo::new(10, <caret>);
        }
    """, "id: u32, val: f64", 1)

    fun testMethod() = checkByText("""
        struct Foo;
        impl Foo { fn bar(&self, id: u32, name: &'static name, year: u16) {} }
        fn main() {
            let foo = Foo{};
            foo.bar(10, "Bo<caret>b", 1987);
        }
    """, "id: u32, name: &'static name, year: u16", 1)

    fun testTraitMethod() = checkByText("""
        trait Named {
            fn greet(&self, text: &'static str, count: u16, l: f64);
        }
        struct Person;
        impl Named for Person {
            fn greet(&self, text: &'static str, count: u16, l: f64) {}
        }
        fn main() {
            let p = Person {};
            p.greet("Hello", 19, 10.21<caret>);
        }
    """, "text: &'static str, count: u16, l: f64", 2)

    fun `test method with explicit self`() = checkByText("""
        struct S;
        impl S { fn foo(self, arg: u32) {} }

        fn main() {
            let s = S;
            S::foo(s, 0<caret>);
        }
    """, "self, arg: u32", 1)

    fun testNotArgs1() = checkByText("""
        fn foo() {}
        fn main() { fo<caret>o(); }
    """, "", -1)

    fun testNotArgs2() = checkByText("""
        fn foo() {}
        fn main() { foo()<caret>; }
    """, "", -1)

    fun testNotAppliedWithinDeclaration() = checkByText("""
        fn foo(v<caret>: u32) {}
    """, "", -1)

    private fun checkByText(code: String, hint: String, index: Int) {
        myFixture.configureByText("main.rs", code)
        val handler = RsParameterInfoHandler()
        val createContext = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)

        // Check hint
        val elt = handler.findElementForParameterInfo(createContext)
        if (hint.isNotEmpty()) {
            elt ?: throw AssertionFailedError("Hint not found")
            handler.showParameterInfo(elt, createContext)
            val items = createContext.itemsToShow ?: throw AssertionFailedError("Parameters are not shown")
            if (items.isEmpty()) throw AssertionFailedError("Parameters are empty")
            val context = MockParameterInfoUIContext(elt)
            handler.updateUI(items[0] as RsArgumentsDescription, context)
            TestCase.assertEquals(hint, handler.hintText)

            // Check parameter index
            val updateContext = MockUpdateParameterInfoContext(myFixture.editor, myFixture.file)
            val element = handler.findElementForUpdatingParameterInfo(updateContext) ?: throw AssertionFailedError("Parameter not found")
            handler.updateParameterInfo(element, updateContext)
            TestCase.assertEquals(index, updateContext.currentParameter)
        } else if (elt != null) {
            throw AssertionFailedError("Unexpected hint found")
        }
    }
}
