/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.lang.core.psi.ext.*

class RsOperatorsResolve : RsResolveTestBase() {

    fun `test arithmetic binary operators`() = testWithAllOps(ArithmeticOp.values()) { traitName, itemName, fnName, sign ->
        """
        #[lang = "$itemName"]
        pub trait $traitName<RHS=Self> {
            type Output;
            fn $fnName(self, rhs: RHS) -> Self::Output;
        }

        struct Foo;
        struct Bar;

        impl $traitName<Bar> for Foo {
            type Output = Foo;
            fn $fnName(self, rhs: Bar) -> Foo { unimplemented!() }
             //X
        }

        fn foo(lhs: Foo, rhs: Bar) {
            let x = lhs $sign rhs;
                      //^
        }
        """
    }

    fun `test equality binary operators`() = testWithAllOps(EqualityOp.values()) { _, _, _, sign ->
        """
        #[lang = "eq"]
        pub trait PartialEq<Rhs: ?Sized = Self> {
            fn eq(&self, other: &Rhs) -> bool;
            fn ne(&self, other: &Rhs) -> bool { !self.eq(other) }
        }

        struct Foo;
        struct Bar;

        impl PartialEq<Bar> for Foo {
            fn eq(&self, rhs: &Bar) -> bool { unimplemented!() }
             //X
        }

        fn foo(lhs: Foo, rhs: Bar) {
            let x = lhs $sign rhs;
                      //^
        }
        """
    }

    fun `test comparison binary operators`() = testWithAllOps(ComparisonOp.values()) { _, _, _, sign ->
        """
        #[lang = "ord"]
        pub trait PartialOrd<Rhs: ?Sized = Self>: PartialEq<Rhs> {
            fn partial_cmp(&self, other: &Rhs) -> Option<Ordering>;
        }

        struct Foo;
        struct Bar;

        impl PartialOrd<Bar> for Foo {
            fn partial_cmp(&self, other: &Bar) -> Option<Ordering> { unimplemented!() }
              //X
        }

        fn foo(lhs: Foo, rhs: Bar) {
            let x = lhs $sign rhs;
                      //^
        }
        """
    }

    fun `test assignment binary operators`() = testWithAllOps(ArithmeticAssignmentOp.values()) { traitName, itemName, fnName, sign ->
        """
        #[lang = "$itemName"]
        pub trait $traitName<Rhs=Self> {
            fn $fnName(&mut self, rhs: Rhs);
        }

        struct Foo;
        struct Bar;

        impl $traitName<Bar> for Foo {
            fn $fnName(&mut self, rhs: Bar) { unimplemented!() }
             //X
        }

        fn foo(lhs: Foo, rhs: Bar) {
            lhs $sign rhs;
              //^
        }
        """
    }

    private inline fun testWithAllOps(operators: List<OverloadableBinaryOperator>,
                                      codeGenerator: (String, String, String, String) -> String) {
        for ((traitName, itemName, fnName, sign) in operators) {
            checkByCode(codeGenerator(traitName, itemName, fnName, sign))
        }
    }
}
