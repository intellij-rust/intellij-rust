/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.AttributeType.*
import org.rust.lang.core.psi.ext.*

enum class AttributeType { Outer, Inner, Both, None }

class RsAttributeTest : RsTestBase() {

    private fun doTest(element: RsElement, type: AttributeType) {
        val isInner = element is RsInnerAttributeOwner
        val hasInner = element is RsDocAndAttributeOwner && element.queryAttributes.hasAttribute("inner")
        val isOuter = element is RsOuterAttributeOwner
        val hasOuter = element is RsDocAndAttributeOwner && element.queryAttributes.hasAttribute("outer")
        check(isInner == hasInner)
        check(isOuter == hasOuter)

        when (type) {
            None -> check(!isInner && !isOuter)
            Both -> check(isInner && isOuter)
            Inner -> check(isInner && !isOuter)
            Outer -> check(!isInner && isOuter)
        }
    }

    private fun doTest(@Language("Rust") code: String, type: AttributeType) {
        InlineFile(code)
        val element = findElementInEditor<RsElement>()

        doTest(element, type)
    }

    private inline fun <reified T : RsElement> doTest2(@Language("Rust") code: String, type: AttributeType) {
        InlineFile(code)
        val element = findElementInEditor<T>()

        doTest(element, type)
    }

    fun `test file`() = doTest("""
        //^
        #![inner]
        """, Inner)

    fun `test extern crate`() = doTest("""
        #[outer]
        extern crate foo;
        //^
        """, Outer)

    fun `test use`() = doTest("""
        #[outer]
        use foo;
        //^
        """, Outer)

    fun `test mod`() = doTest("""
        #[outer]
        mod m {
        //^
            #![inner]
        }
        """, Both)

    fun `test mod separate file`() = doTest("""
        #[outer]
        mod m;
        //^
        """, Outer)

    fun `test extern`() = doTest2<RsForeignModItem>("""
        #[outer]
        extern {
        //^
            #![inner]
        }
        """, Both)

    fun `test trait`() = doTest("""
        #[outer]
        trait T {
        //^
            #![inner]
        }
        """, Outer)

    fun `test struct`() = doTest("""
        #[outer]
        struct S { }
        //^
        """, Outer)

    fun `test struct item`() = doTest("""
        struct S {
            #[outer]
            s: u16,
          //^
        }
        """, Outer)

    fun `test union`() = doTest("""
        #[outer]
        union U {
        //^
            u1: u16,
        }
        """, Outer)

    fun `test union field`() = doTest("""
        union U {
            #[outer]
            u1: u16,
          //^
            u2: i16,
        }
        """, Outer)

    fun `test enum`() = doTest("""
        #[outer]
        enum E { }
        //^
        """, Outer)

    fun `test enum variant`() = doTest("""
        enum E {
            #[outer]
            EV,
          //^
        }
        """, Outer)

    fun `test impl`() = doTest("""
        struct S { }
        #[outer]
        impl S {
        //^
            #![inner]
        }
        """, Both)

    fun `test impl trait`() = doTest("""
        struct S { }
        trait T { }
        #[outer]
        impl T for S {
        //^
            #![inner]
        }
        """, Both)

    fun `test fn`() = doTest("""
        #[outer]
        fn foo() {
         //^
            #![inner]
        }
        """, Both)

    fun `test fn type parameter`() = doTest("""
        fn foo<#[outer] T>( a: u16) {
                      //^
        }
        """, Outer)

    fun `test fn lifetime parameter`() = doTest("""
        fn foo<#[outer] 'a>( a: u16) {
                      //^
        }
        """, Outer)

    fun `test type alias`() = doTest("""
        #[outer]
        type T = u16;
        //^
        """, Outer)

    fun `test const`() = doTest("""
        #[outer]
        const C: u16 = 1;
        //^
        """, Outer)

    fun `test static`() = doTest("""
        #[outer]
        static S: u16 = 1;
        //^
        """, Outer)

    fun `test macro`() = doTest("""
        #[outer]
        macro_rules! makro {
        //^
            () => { };
        }
        """, Outer)

    fun `test macro call`() = doTest("""
        macro_rules! makro {
            () => { };
        }

        #[outer]
        makro!();
        //^
        """, Outer)

    fun `test let statement`() = expect<IllegalStateException> {
        doTest2<RsStmt>("""
        fn main() {
            #[outer]
            let a = 1u16;
            //^
        }
        """, Outer)
    }

    fun `test fn call expr as statement`() = expect<IllegalStateException> {
        doTest2<RsStmt>("""
        fn a() {
        }
        fn main() {
            #[outer]
            a();
          //^
        }
        """, Outer)
    }

    fun `test literal expr as statement`() = expect<IllegalStateException> {
        doTest("""
        fn main() {
            #[outer]
            "Hello Rust!";
            //^
        }
        """, Outer)
    }
}
