/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.refactoring.BaseRefactoringProcessor
import org.intellij.lang.annotations.Language
import org.rust.MockAdditionalCfgOptions
import org.rust.MockEdition
import org.rust.RsTestBase
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ide.refactoring.changeSignature.Parameter
import org.rust.ide.refactoring.changeSignature.ParameterProperty
import org.rust.ide.refactoring.changeSignature.RsChangeFunctionSignatureConfig
import org.rust.ide.refactoring.changeSignature.withMockChangeFunctionSignature
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.stdext.removeLast

class RsChangeSignatureTest : RsTestBase() {
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test unavailable if a parameter is cfg-disabled`() = checkError("""
        fn foo/*caret*/(#[cfg(not(intellij_rust))] a: u32) {}
    """, """Cannot perform refactoring.
Cannot change signature of function with cfg-disabled parameters""")

    fun `test unavailable inside function`() = checkError("""
        fn foo() {
            let a/*caret*/ = 5;
        }
    """, "The caret should be positioned at a function or method")


    fun `test unavailable on unresolved function call`() = checkError("""
        fn bar(a: u32) {}
        fn baz() {
            bar(foo(/*caret*/));
        }
    """, "The caret should be positioned at a function or method")

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test available if a parameter is cfg-enabled`() = doTest("""
        fn foo/*caret*/(#[cfg(intellij_rust)] a: u32) {}
    """, """
        fn bar(#[cfg(intellij_rust)] a: u32) {}
    """) {
        name = "bar"
    }

    fun `test do not change anything`() = doTest("""
        async unsafe fn foo/*caret*/(a: u32, b: bool) -> u32 { 0 }
        fn bar() {
            unsafe { foo(1, true); }
        }
    """, """
        async unsafe fn foo(a: u32, b: bool) -> u32 { 0 }
        fn bar() {
            unsafe { foo(1, true); }
        }
    """) {}

    fun `test rename function reference`() = doTest("""
        fn foo/*caret*/() {}
        fn id<T>(t: T) {}

        fn baz() {
            id(foo)
        }
    """, """
        fn bar/*caret*/() {}
        fn id<T>(t: T) {}

        fn baz() {
            id(bar)
        }
    """) {
        name = "bar"
    }

    fun `test rename function import`() = doTest("""
        mod bar {
            pub fn foo/*caret*/() {}
        }
        use bar::{foo};
    """, """
        mod bar {
            pub fn baz/*caret*/() {}
        }
        use bar::{baz};
    """) {
        name = "baz"
    }

    fun `test rename function`() = doTest("""
        fn foo/*caret*/() {}
    """, """
        fn bar() {}
    """) {
        name = "bar"
    }

    fun `test rename function change usage`() = doTest("""
        fn foo/*caret*/() {}
        fn test() {
            foo()
        }
    """, """
        fn bar() {}
        fn test() {
            bar()
        }
    """) {
        name = "bar"
    }

    fun `test rename function change complex path usage`() = doTest("""
        mod inner {
            pub fn foo/*caret*/() {}
        }
        fn test() {
            inner::foo()
        }
    """, """
        mod inner {
            pub fn bar() {}
        }
        fn test() {
            inner::bar()
        }
    """) {
        name = "bar"
    }

    fun `test rename method change usage`() = doTest("""
        struct S;
        impl S {
            fn foo/*caret*/(&self) {}
        }

        fn test(s: S) {
            s.foo();
        }
    """, """
        struct S;
        impl S {
            fn bar(&self) {}
        }

        fn test(s: S) {
            s.bar();
        }
    """) {
        name = "bar"
    }

    fun `test change visibility`() = doTest("""
        pub fn foo/*caret*/() {}
    """, """
        pub(crate) fn foo() {}
    """) {
        visibility = createVisibility("pub(crate)")
    }

    fun `test remove visibility`() = doTest("""
        pub fn foo/*caret*/() {}
    """, """
        fn foo() {}
    """) {
        visibility = null
    }

    fun `test add visibility with attribute`() = doTest("""
        #[attr]
        fn foo/*caret*/() {}
    """, """
        #[attr]
        pub fn foo() {}
    """) {
        visibility = createVisibility("pub")
    }

    fun `test add visibility with comment`() = doTest("""
        // comment
        fn foo/*caret*/() {}
    """, """
        // comment
        pub fn foo() {}
    """) {
        visibility = createVisibility("pub")
    }

    fun `test add visibility with attribute and comment`() = doTest("""
        // comment
        #[attr]
        fn foo/*caret*/() {}
    """, """
        // comment
        #[attr]
        pub fn foo() {}
    """) {
        visibility = createVisibility("pub")
    }

    fun `test change return type`() = doTest("""
        fn foo/*caret*/() -> i32 { 0 }
    """, """
        fn foo() -> u32 { 0 }
    """) {
        returnTypeDisplay = createType("u32")
    }

    fun `test change return type lifetime`() = doTest("""
        fn foo<'a, 'b>/*caret*/(a: &'a u32, b: &'b u32) -> &'a i32 { 0 }
    """, """
        fn foo<'a, 'b>(a: &'a u32, b: &'b u32) -> &'b i32 { 0 }
    """) {
        returnTypeDisplay = createType("&'b i32")
    }

    fun `test add return type`() = doTest("""
        fn foo/*caret*/() {}
    """, """
        fn foo() -> u32 {}
    """) {
        returnTypeDisplay = createType("u32")
    }

    fun `test add return type with lifetime`() = doTest("""
        fn foo/*caret*/<'a>(a: &'a u32) { a }
                          //^
    """, """
        fn foo/*caret*/<'a>(a: &'a u32) -> &'a u32 { a }
                          //^
    """) {
        returnTypeDisplay = createType("&'a u32")
    }

    fun `test add return type with default type arguments`() = doTest("""
        struct S<T, R=u32>(T, R);
        fn foo/*caret*/(s: S<bool>) { unimplemented!() }
                      //^
    """, """
        struct S<T, R=u32>(T, R);
        fn foo/*caret*/(s: S<bool>) -> S<bool> { unimplemented!() }
                      //^
    """) {
        val parameter = findElementInEditor<RsValueParameter>()
        returnTypeDisplay = parameter.typeReference!!
    }

    fun `test remove return type`() = doTest("""
        fn foo/*caret*/() -> u32 { 0 }
    """, """
        fn foo() { 0 }
    """) {
        returnTypeDisplay = createType("()")
    }

    fun `test remove return type without block`() = doTest("""
        trait Trait {
            fn foo/*caret*/() -> i32;
        }
    """, """
        trait Trait {
            fn foo() -> u32;
        }
    """) {
        returnTypeDisplay = createType("u32")
    }

    fun `test remove only parameter`() = doTest("""
        fn foo/*caret*/(a: u32) {
            let c = a;
        }
        fn bar() {
            foo(0);
        }
    """, """
        fn foo() {
            let c = a;
        }
        fn bar() {
            foo();
        }
    """) {
        parameters.removeAt(0)
    }

    fun `test remove first parameter`() = doTest("""
        fn foo/*caret*/(a: u32, b: u32) {
            let c = a;
        }
        fn bar() {
            foo(0, 1);
        }
    """, """
        fn foo(b: u32) {
            let c = a;
        }
        fn bar() {
            foo(1);
        }
    """) {
        parameters.removeAt(0)
    }

    fun `test remove middle parameter`() = doTest("""
        fn foo/*caret*/(a: u32, b: u32, c: u32) {
            let c = a;
        }
        fn bar() {
            foo(0, 1, 2);
        }
    """, """
        fn foo(a: u32, c: u32) {
            let c = a;
        }
        fn bar() {
            foo(0, 2);
        }
    """) {
        parameters.removeAt(1)
    }

    fun `test remove last parameter`() = doTest("""
        fn foo/*caret*/(a: u32, b: u32) {}
        fn bar() {
            foo(0, 1);
        }
    """, """
        fn foo(a: u32) {}
        fn bar() {
            foo(0);
        }
    """) {
        parameters.removeLast()
    }

    fun `test remove last parameter (multiline)`() = doTest("""
        fn foo/*caret*/(
            a: u32,
            b: u32,
        ) {}
        fn bar() {
            foo(
                0,
                1
            );
        }
    """, """
        fn foo(a: u32) {}
        fn bar() {
            foo(0);
        }
    """) {
        parameters.removeLast()
    }

    fun `test remove last method parameter (multiline)`() = doTest("""
        struct S;
        impl S {
            fn foo/*caret*/(
                &self,
                a: u32,
            ) {}
        }
        fn bar(s: S) {
            s.foo(
                0,
            );
        }
    """, """
        struct S;
        impl S {
            fn foo(&self) {}
        }
        fn bar(s: S) {
            s.foo();
        }
    """) {
        parameters.removeLast()
    }

    fun `test remove parameter trailing comma`() = doTest("""
        fn foo/*caret*/(a: u32, b: u32,) {}
    """, """
        fn foo(a: u32) {}
    """) {
        parameters.removeLast()
    }

    fun `test remove method parameter trailing comma`() = doTest("""
        struct S;
        impl S {
            fn foo/*caret*/(&self, a: u32, b: u32,) {}
        }
    """, """
        struct S;
        impl S {
            fn foo/*caret*/(&self, a: u32) {}
        }
    """) {
        parameters.removeLast()
    }

    fun `test remove last parameter trailing comma`() = doTest("""
        fn foo/*caret*/(a: u32,) {}
    """, """
        fn foo() {}
    """) {
        parameters.clear()
    }

    fun `test remove last method parameter trailing comma`() = doTest("""
        struct S;
        impl S {
            fn foo/*caret*/(&self, a: u32,) {}
        }
    """, """
        struct S;
        impl S {
            fn foo/*caret*/(&self) {}
        }
    """) {
        parameters.clear()
    }

    fun `test add only parameter`() = doTest("""
        fn foo/*caret*/() {}
        fn bar() {
            foo();
        }
    """, """
        fn foo(a: u32) {}
        fn bar() {
            foo();
        }
    """) {
        parameters.add(parameter("a", "u32"))
    }

    fun `test add last parameter`() = doTest("""
        fn foo/*caret*/(a: u32) {}
        fn bar() {
            foo(0);
        }
    """, """
        fn foo(a: u32, b: u32) {}
        fn bar() {
            foo(0, );
        }
    """) {
        parameters.add(parameter("b", "u32"))
    }

    fun `test add parameter in the middle (multiline)`() = doTest("""
        fn foo/*caret*/(
            a: u32,
            c: u32,
        ) {}
        fn bar() {
            foo(
                0,
                1,
            );
        }
    """, """
        fn foo(
            a: u32,
            b: u32,
            c: u32,
        ) {}
        fn bar() {
            foo(
                0,
                ,
                1,
            );
        }
    """) {
        parameters.add(1, parameter("b", "u32"))
    }

    fun `test add multiple parameters`() = doTest("""
        fn foo/*caret*/(a: u32) {}
        fn bar() {
            foo(0);
        }
    """, """
        fn foo(a: u32, b: u32, c: u32) {}
        fn bar() {
            foo(0, , );
        }
    """) {
        parameters.add(parameter("b", "u32"))
        parameters.add(parameter("c", "u32"))
    }

    fun `test add parameter with lifetime`() = doTest("""
        fn foo/*caret*/<'a>(a: &'a u32) {}
                          //^
    """, """
        fn foo/*caret*/<'a>(a: &'a u32, b: &'a u32) {}
                          //^
    """) {
        val parameter = findElementInEditor<RsValueParameter>()
        parameters.add(parameter("b", parameter.typeReference!!))
    }

    fun `test add parameter with default type arguments`() = doTest("""
        struct S<T, R=u32>(T, R);
        fn foo/*caret*/(a: S<bool>) { unimplemented!() }
                      //^
    """, """
        struct S<T, R=u32>(T, R);
        fn foo/*caret*/(a: S<bool>, b: S<bool>) { unimplemented!() }
                      //^
    """) {
        val parameter = findElementInEditor<RsValueParameter>()
        parameters.add(parameter("b", parameter.typeReference!!))
    }

    fun `test add parameter to method`() = doTest("""
        struct S;
        impl S {
            fn foo/*caret*/(&self) {}
        }
        fn bar(s: S) {
            s.foo();
        }
    """, """
        struct S;
        impl S {
            fn foo/*caret*/(&self, a: u32) {}
        }
        fn bar(s: S) {
            s.foo();
        }
    """) {
        parameters.add(parameter("a", "u32"))
    }

    fun `test add only parameter with default value`() = doTest("""
        fn foo/*caret*/() {}
        fn bar() {
            foo();
        }
    """, """
        fn foo(a: u32) {}
        fn bar() {
            foo(10);
        }
    """) {
        parameters.add(parameter("a", "u32", defaultValue = createExpr("10")))
    }

    fun `test add last parameter with default value`() = doTest("""
        fn foo/*caret*/(a: u32) {}
        fn bar() {
            foo(0);
        }
    """, """
        fn foo(a: u32, b: u32) {}
        fn bar() {
            foo(0, 10);
        }
    """) {
        parameters.add(parameter("b", "u32", defaultValue = createExpr("10")))
    }

    fun `test import default value type`() = doTest("""
        mod foo {
            pub struct S(u32);
            pub fn bar/*caret*/(a: u32) {}
                 //^
        }

        fn baz() {
            foo::bar(0);
        }
    """, """
        use foo::S;

        mod foo {
            pub struct S(u32);
            pub fn bar(a: u32, b: S) {}
                 //^
        }

        fn baz() {
            foo::bar(0, S(1));
        }
    """) {
        parameters.add(parameter("b", "S", defaultValue = createExprWithContext("S(1)", function)))
    }

    fun `test import default value type inside path`() = doTest("""
        mod foo {
            pub enum Option<T> {
                Some(T),
                None
            }

            pub struct S1<T>(pub Option<T>);
            pub struct S2;
            pub fn bar/*caret*/(a: u32) {}
                 //^
        }

        fn baz() {
            foo::bar(0);
        }
    """, """
        use foo::{Option, S1, S2};

        mod foo {
            pub enum Option<T> {
                Some(T),
                None
            }

            pub struct S1<T>(pub Option<T>);
            pub struct S2;
            pub fn bar/*caret*/(a: u32, b: S1<S2>) {}
                 //^
        }

        fn baz() {
            foo::bar(0, S1::<S2>(Option::None));
        }
    """) {
        parameters.add(parameter("b", "S1<S2>", defaultValue = createExprWithContext("S1::<S2>(Option::None)", function)))
    }

    fun `test swap parameters`() = doTest("""
        fn foo/*caret*/(a: u32, b: u32) {}
        fn bar() {
            foo(0, 1);
        }
    """, """
        fn foo(b: u32, a: u32) {}
        fn bar() {
            foo(1, 0);
        }
    """) {
        swapParameters(0, 1)
    }

    fun `test swap method parameters`() = doTest("""
        struct S;
        impl S {
            fn foo/*caret*/(&self, a: u32, b: u32) {}
        }
        fn bar(s: S) {
            s.foo(0, 1);
        }
    """, """
        struct S;
        impl S {
            fn foo/*caret*/(&self, b: u32, a: u32) {}
        }
        fn bar(s: S) {
            s.foo(1, 0);
        }
    """) {
        swapParameters(0, 1)
    }

    fun `test remove only method parameter`() = doTest("""
        struct S;
        impl S {
            fn foo/*caret*/(&self, a: u32) {}
        }
        fn bar(s: S) {
            S::foo(&s, 0);
        }
    """, """
        struct S;
        impl S {
            fn foo/*caret*/(&self) {}
        }
        fn bar(s: S) {
            S::foo(&s);
        }
    """) {
        parameters.clear()
    }

    fun `test swap method parameters UFCS`() = doTest("""
        struct S;
        impl S {
            fn foo/*caret*/(&self, a: u32, b: u32) {}
        }
        fn bar(s: S) {
            S::foo(&s, 0, 1);
        }
    """, """
        struct S;
        impl S {
            fn foo/*caret*/(&self, b: u32, a: u32) {}
        }
        fn bar(s: S) {
            S::foo(&s, 1, 0);
        }
    """) {
        swapParameters(0, 1)
    }

    fun `test add method parameter UFCS`() = doTest("""
        struct S;
        impl S {
            fn foo/*caret*/(&self) {}
        }
        fn bar(s: S) {
            S::foo(&s);
        }
    """, """
        struct S;
        impl S {
            fn foo/*caret*/(&self, a: u32) {}
        }
        fn bar(s: S) {
            S::foo(&s, );
        }
    """) {
        parameters.add(parameter("a", "u32"))
    }

    fun `test delete method parameter UFCS`() = doTest("""
        struct S;
        impl S {
            fn foo/*caret*/(&self, a: u32, b: u32) {}
        }
        fn bar(s: S) {
            S::foo(&s, 0, 1);
        }
    """, """
        struct S;
        impl S {
            fn foo/*caret*/(&self, b: u32) {}
        }
        fn bar(s: S) {
            S::foo(&s, 1);
        }
    """) {
        parameters.removeAt(0)
    }

    fun `test swap parameters with comments`() = doTest("""
        fn foo/*caret*/( /*a0*/ a /*a1*/ : u32 /*a2*/ , /*b0*/ b: u32 /*b1*/ ) {}
        fn bar() {
            foo(0, 1);
        }
    """, """
        fn foo(/*b0*/ b: u32 /*b1*/, /*a0*/ a /*a1*/ : u32 /*a2*/) {}
        fn bar() {
            foo(1, 0);
        }
    """) {
        swapParameters(0, 1)
    }

    fun `test swap arguments with comments`() = doTest("""
        fn foo/*caret*/(a: u32, b: u32) {}
        fn bar() {
            foo( /*a0*/ 0 /*a1*/  /*a2*/ , /*b0*/ 1 /*b1*/ );
        }
    """, """
        fn foo(b: u32, a: u32) {}
        fn bar() {
            foo(/*b0*/ 1 /*b1*/, /*a0*/ 0 /*a1*/  /*a2*/);
        }
    """) {
        swapParameters(0, 1)
    }

    fun `test multiple move`() = doTest("""
        fn foo/*caret*/(a: u32, b: u32, c: u32) {}
        fn bar() {
            foo(0, 1, 2);
        }
    """, """
        fn foo(b: u32, c: u32, a: u32) {}
        fn bar() {
            foo(1, 2, 0);
        }
    """) {
        swapParameters(0, 1)
        swapParameters(1, 2)
    }

    fun `test swap back`() = doTest("""
        fn foo/*caret*/(a: u32, b: u32, c: u32) {}
        fn bar() {
            foo(0, 1, 2);
        }
    """, """
        fn foo(a: u32, b: u32, c: u32) {}
        fn bar() {
            foo(0, 1, 2);
        }
    """) {
        swapParameters(0, 1)
        swapParameters(1, 0)
    }

    fun `test move and add parameter`() = doTest("""
        fn foo/*caret*/(a: u32, b: u32) {}
        fn bar() {
            foo(0, 1);
        }
    """, """
        fn foo(b: u32, a: u32) {}
        fn bar() {
            foo(1, );
        }
    """) {
        parameters[0] = parameters[1]
        parameters[1] = parameter("a", "u32")
    }

    fun `test rename parameter ident with ident`() = doTest("""
        fn foo/*caret*/(a: u32) {
            let _ = a;
            let _ = a + 1;
        }
    """, """
        fn foo(b: u32) {
            let _ = b;
            let _ = b + 1;
        }
    """) {
        parameters[0].patText = "b"
    }

    fun `test rename parameter complex pat with ident`() = doTest("""
        fn foo/*caret*/((a, b): (u32, u32)) {
            let _ = a;
        }
    """, """
        fn foo(x: (u32, u32)) {
            let _ = a;
        }
    """) {
        parameters[0].patText = "x"
    }

    fun `test rename parameter ident with complex pat`() = doTest("""
        fn foo/*caret*/(a: (u32, u32)) {
            let _ = a;
        }
    """, """
        fn foo((x, y): (u32, u32)) {
            let _ = a;
        }
    """) {
        parameters[0].patText = "(x, y)"
    }

    fun `test change parameter type`() = doTest("""
        fn foo/*caret*/(a: u32) {}
    """, """
        fn foo(a: i32) {}
    """) {
        parameters[0].type = createParamType("i32")
    }

    fun `test wrong argument count`() = doTest("""
        fn foo/*caret*/(a: u32) {}
        fn bar() {
            foo(1, 2, 3)
        }
    """, """
        fn foo() {}
        fn bar() {
            foo(1, 2, 3)
        }
    """) {
        parameters.clear()
    }

    fun `test add async`() = doTest("""
        fn foo/*caret*/(a: u32) {}
    """, """
        async fn foo(a: u32) {}
    """) {
        isAsync = true
    }

    fun `test remove async`() = doTest("""
        async fn foo/*caret*/(a: u32) {}
    """, """
        fn foo(a: u32) {}
    """) {
        isAsync = false
    }

    fun `test add unsafe`() = doTest("""
        fn foo/*caret*/(a: u32) {}
    """, """
        unsafe fn foo(a: u32) {}
    """) {
        isUnsafe = true
    }

    fun `test remove unsafe`() = doTest("""
        unsafe fn foo/*caret*/(a: u32) {}
    """, """
        fn foo(a: u32) {}
    """) {
        isUnsafe = false
    }

    fun `test add async unsafe and visibility`() = doTest("""
        fn foo/*caret*/(a: u32) {}
    """, """
        pub async unsafe fn foo(a: u32) {}
    """) {
        isAsync = true
        isUnsafe = true
        visibility = createVisibility("pub")
    }

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import return type in different module`() = doTest("""
        mod foo {
            pub struct S;
                     //^
        }
        mod bar {
            fn baz/*caret*/() {}
        }
    """, """
        mod foo {
            pub struct S;
                     //^
        }
        mod bar {
            use crate::foo::S;

            fn baz/*caret*/() -> S {}
        }
    """) {
        returnTypeDisplay = referToType("S", findElementInEditor<RsStructItem>())
    }

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import new parameter type in different module`() = doTest("""
        mod foo {
            pub struct S;
                     //^
        }
        mod bar {
            fn baz/*caret*/() {}
        }
    """, """
        mod foo {
            pub struct S;
                     //^
        }
        mod bar {
            use crate::foo::S;

            fn baz/*caret*/(s: S) {}
        }
    """) {
        parameters.add(parameter("s", referToType("S", findElementInEditor<RsStructItem>())))
    }

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import changed parameter type in different module`() = doTest("""
        mod foo {
            pub struct S;
                     //^
        }
        mod bar {
            fn baz/*caret*/(s: u32) {}
        }
    """, """
        mod foo {
            pub struct S;
                     //^
        }
        mod bar {
            use crate::foo::S;

            fn baz/*caret*/(s: S) {}
        }
    """) {
        parameters[0].type = ParameterProperty.Valid(referToType("S", findElementInEditor<RsStructItem>()))
    }

    fun `test name conflict module`() = checkConflicts("""
        fn foo/*caret*/() {}
        fn bar() {}
    """, setOf("The name bar conflicts with an existing item in main.rs (in test_package)")) {
        name = "bar"
    }

    fun `test name conflict impl`() = checkConflicts("""
        struct S;

        impl S {
            fn foo/*caret*/() {}
            fn bar() {}
        }
    """, setOf("The name bar conflicts with an existing item in impl S (in test_package)")) {
        name = "bar"
    }

    fun `test name conflict trait`() = checkConflicts("""
        struct S;
        trait Trait {
            fn foo/*caret*/();
            fn bar();
        }
    """, setOf("The name bar conflicts with an existing item in Trait (in test_package)")) {
        name = "bar"
    }

    fun `test visibility conflict function call`() = checkConflicts("""
        mod foo {
            pub fn bar/*caret*/() {}
        }
        fn baz() {
            foo::bar();
        }
    """, setOf("The function will not be visible from test_package after the refactoring")) {
        visibility = null
    }

    fun `test visibility conflict method call`() = checkConflicts("""
        mod foo {
            pub struct S;
            impl S {
                pub fn bar/*caret*/(&self) {}
            }
        }
        mod foo2 {
            fn baz(s: super::foo::S) {
                s.bar();
            }
        }
    """, setOf("The function will not be visible from test_package::foo2 after the refactoring")) {
        visibility = null
    }

    fun `test no visibility conflict module`() = doTest("""
        mod foo {}
        fn foo/*caret*/() {}
    """, """
        mod foo {}
        fn foo/*caret*/() {}
    """) {}

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test no visibility conflict disabled function`() = doTest("""
        fn bar/*caret*/() {}

        #[cfg(not(intellij_rust))]
        fn foo() {}
    """, """
        fn foo/*caret*/() {}

        #[cfg(not(intellij_rust))]
        fn foo() {}
    """) {
        name = "foo"
    }

    fun `test no visibility conflict restricted mod`() = doTest("""
        mod foo2 {
            mod foo {
                fn bar/*caret*/() {}
            }
            fn baz() {
                foo::bar();
            }
        }

    """, """
        mod foo2 {
            mod foo {
                pub(in super) fn bar/*caret*/() {}
            }
            fn baz() {
                foo::bar();
            }
        }

    """) {
        visibility = createVisibility("pub(in super)")
    }

    private val overriddenMethodWithUsagesBefore: String = """
        trait Trait {
            fn foo/*trait*/(&self);
        }

        struct S;
        impl Trait for S {
            fn foo/*impl*/(&self) {}
        }

        fn bar1(t: &dyn Trait) {
            t.foo();
        }
        fn bar2(s: S) {
            s.foo();
        }
        fn bar3<T: Trait>(t: &T) {
            t.foo();
        }
    """

    private val overriddenMethodWithUsagesAfter: String = """
        trait Trait {
            fn bar(&self) -> u32;
        }

        struct S;
        impl Trait for S {
            fn bar(&self) -> u32 {}
        }

        fn bar1(t: &dyn Trait) {
            t.bar();
        }
        fn bar2(s: S) {
            s.bar();
        }
        fn bar3<T: Trait>(t: &T) {
            t.bar();
        }
    """

    fun `test change overridden methods and usages when invoked on trait`() = doTest(
        overriddenMethodWithUsagesBefore.replace("/*trait*/", "/*caret*/").replace("/*impl*/", ""),
        overriddenMethodWithUsagesAfter
    ) {
        name = "bar"
        returnTypeDisplay = createType("u32")
    }

    fun `test change overridden methods and usages when invoked on impl`() = doTest(
        overriddenMethodWithUsagesBefore.replace("/*impl*/", "/*caret*/").replace("/*trait*/", ""),
        overriddenMethodWithUsagesAfter
    ) {
        name = "bar"
        returnTypeDisplay = createType("u32")
    }

    fun `test change called function`() = doTest("""
        fn foo() {}
        fn baz() {
            foo/*caret*/();
        }
    """, """
        fn foo2() {}
        fn baz() {
            foo2();
        }
    """) {
        name = "foo2"
    }

    fun `test change nested called function`() = doTest("""
        fn foo() -> u32 { 0 }
        fn bar(a: u32) {}
        fn baz() {
            bar(foo(/*caret*/));
        }
    """, """
        fn foo2() -> u32 { 0 }
        fn bar(a: u32) {}
        fn baz() {
            bar(foo2());
        }
    """) {
        name = "foo2"
    }

    fun `test change called method`() = doTest("""
        struct S;
        impl S {
            fn foo(&self) {}
        }
        fn baz(s: S) {
            s.foo/*caret*/();
        }
    """, """
        struct S;
        impl S {
            fn foo2(&self) {}
        }
        fn baz(s: S) {
            s.foo2();
        }
    """) {
        name = "foo2"
    }

    fun `test do not import default type arguments`() = doTest("""
        mod foo {
            pub struct S;
            pub struct Vec<T = S>(T);

            fn bar(t: Vec) {}
                      //^
        }

        fn bar/*caret*/() {}
    """, """
        use foo::Vec;

        mod foo {
            pub struct S;
            pub struct Vec<T = S>(T);

            fn bar(t: Vec) {}
                      //^
        }

        fn bar/*caret*/(a: Vec) -> Vec {}
    """) {
        val vec = findElementInEditor<RsTypeReference>()
        parameters.add(parameter("a", vec))
        returnTypeDisplay = vec
    }

    private fun RsChangeFunctionSignatureConfig.swapParameters(a: Int, b: Int) {
        val param = parameters[a]
        parameters[a] = parameters[b]
        parameters[b] = param
    }

    private fun createVisibility(vis: String): RsVis = RsPsiFactory(project).createVis(vis)
    private fun createType(text: String): RsTypeReference = RsPsiFactory(project).createType(text)
    private fun createExpr(text: String): RsExpr = RsPsiFactory(project).createExpression(text)
    private fun createParamType(text: String): ParameterProperty<RsTypeReference> = ParameterProperty.Valid(createType(text))
    private fun parameter(patText: String, type: String, defaultValue: RsExpr? = null): Parameter
        = parameter(patText, createType(type), defaultValue = defaultValue)
    private fun parameter(patText: String, type: RsTypeReference, defaultValue: RsExpr? = null): Parameter {
        val parameterDefaultValue = if (defaultValue != null) {
            ParameterProperty.Valid(defaultValue)
        } else {
            ParameterProperty.Empty()
        }
        return Parameter(RsPsiFactory(project), patText, ParameterProperty.Valid(type),
            defaultValue = parameterDefaultValue)
    }

    /**
     * Refer to existing type in the test code snippet.
     */
    private fun referToType(text: String, context: RsElement): RsTypeReference
        = RsTypeReferenceCodeFragment(myFixture.project, text, context).typeReference!!

    private fun createExprWithContext(text: String, context: RsElement): RsExpr
        = RsExpressionCodeFragment(myFixture.project, text, context).expr!!

    private fun doTest(
        @Language("Rust") code: String,
        @Language("Rust") expected: String,
        modifyConfig: RsChangeFunctionSignatureConfig.() -> Unit
    ) {
        withMockChangeFunctionSignature({ config ->
            modifyConfig.invoke(config)
        }) {
            checkEditorAction(code, expected, "ChangeSignature")
        }
    }

    private fun checkConflicts(
        @Language("Rust") code: String,
        expectedConflicts: Set<String>,
        modifyConfig: RsChangeFunctionSignatureConfig.() -> Unit
    ) {
        try {
            doTest(code, code, modifyConfig)
            if (expectedConflicts.isNotEmpty()) {
                error("No conflicts found, expected $expectedConflicts")
            }
        }
        catch (e: BaseRefactoringProcessor.ConflictsInTestsException) {
            assertEquals(expectedConflicts, e.messages.toSet())
        }
    }

    private fun checkError(@Language("Rust") code: String, errorMessage: String) {
        try {
            checkEditorAction(code, code, "ChangeSignature")
            error("No error found, expected $errorMessage")
        } catch (e: Exception) {
            assertEquals(errorMessage, e.message)
        }
    }
}
