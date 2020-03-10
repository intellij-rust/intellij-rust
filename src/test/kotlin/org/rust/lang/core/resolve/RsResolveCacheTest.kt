/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapiext.Testmark
import com.intellij.psi.PsiDocumentManager
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsWeakReferenceElement
import org.rust.lang.core.resolve.ref.RsResolveCache.Testmarks

class RsResolveCacheTest : RsTestBase() {
    fun `test cache invalidated on rust structure change`() = checkResolvedToXY("""
        mod a { pub struct S; }
                         //X
        mod b { pub struct S; }
                         //Y
        use a/*caret*/::S;
        type T = S;
               //^
    """, "\bb", Testmarks.rustStructureDependentCacheCleared)

    fun `test resolve correctly without global cache invalidation 1`() = checkResolvedToXY("""
        struct S1;
             //X
        struct S2;
             //Y
        fn main() {
            let a: S1/*caret*/;
        }        //^
    """, "\b2", Testmarks.removeChangedElement)

    fun `test resolve correctly without global cache invalidation 2`() = checkResolvedToXY("""
        mod a { pub struct S; }
                         //X
        mod b { pub struct S; }
                         //Y
        fn main() {
            let _: a/*caret*/
                ::S;
                //^
        }
    """, "\bb", Testmarks.removeChangedElement)

    fun `test resolve correctly without global cache invalidation 3`() = checkResolvedToXY("""
        struct S;
        trait Trait1 { type Item; }
        trait Trait2 { type Item; }
        impl Trait1 for S { type Item = (); }
                               //X
        impl Trait2 for S { type Item = (); }
                               //Y
        fn main() {
            let _: <S as Trait1/*caret*/>
                ::Item;
                //^
        }
    """, "\b2", Testmarks.removeChangedElement)

    fun `test resolve correctly without global cache invalidation 4`() = checkResolvedToXY("""
        mod foo { pub struct S; }
           //Y
        mod bar {
            mod foo { pub struct S; }
               //X
            fn baz() {
                /*caret*/
                foo
                //^
                ::S;
            }
        }
    """, "::")

    fun `test edit local pat binding`() = checkResolvedAndThenUnresolved("""
        fn main() {
            let a/*caret*/ = 0;
            a;//X
        } //^
    """, "1")

    fun `test edit fn-signature pat binding`() = checkResolvedAndThenUnresolved("""
        fn foo(a/*caret*/: i32) {
             //X
            a;
        } //^
    """, "1")

    fun `test edit label declaration`() = checkResolvedAndThenUnresolved("""
        fn main() {
            'label/*caret*/: loop {
             //X
                break 'label
            }         //^
        }
    """, "1")

    fun `test edit label usage`() = checkResolvedToXY("""
        fn main() {
            'label1: loop {
              //X
                'label2: loop {
                 //Y
                    break 'label1/*caret*/
                }         //^
            }
        }
    """, "\b2")

    fun `test edit local lifetime declaration`() = checkResolvedAndThenUnresolved("""
        fn main() {
            let _: &dyn for<'lifetime/*caret*/>
                            //X
                Trait<'lifetime>;
        }               //^
    """, "1")

    fun `test edit local lifetime usage`() = checkResolvedToXY("""
        fn main() {
            let _: &dyn for<'lifetime1,
                            //X
                            'lifetime2>
                            //Y
                Trait<'lifetime1/*caret*/>;
        }               //^
    """, "\b2")

    fun `test edit fn-signature lifetime declaration`() = checkResolvedAndThenUnresolved("""
        fn foo<'lifetime/*caret*/>() {
                //X
            let _: &dyn Trait<'lifetime>;
        }                     //^
    """, "1")

    fun `test edit fn-signature lifetime usage`() = checkResolvedToXY("""
        fn foo<'lifetime1,
                //X
                'lifetime2>() {
                //Y
            let _: &dyn Trait<'lifetime1/*caret*/>;
        }                     //^
    """, "\b2")

    fun `test edit macro meta variable declaration`() = checkResolvedAndThenUnresolved("""
        macro_rules! foo {
            ($ item_var/*caret*/:item) => {
                //X
                $ item_var
            };    //^
        }
    """, "1")

    fun `test edit macro meta variable usage`() = checkResolvedToXY("""
        macro_rules! foo {
            ($ item_var1:item,
                //X
            $ item_var2:item) => {
                //Y
                $ item_var1/*caret*/
            };    //^
        }
    """, "\b2")

    fun `test struct literal field reference edit field declaration`() = checkResolvedAndThenUnresolved("""
        struct S {
            field/*caret*/: i32
        }   //X
        fn main() {
            S { field: 0 };
        }       //^
    """, "1")

    fun `test struct literal field reference edit usage`() = checkResolvedToXY("""
        struct S {
            field1: i32,
            //X
            field2: i32
        }   //Y
        fn main() {
            S { field1/*caret*/: 0 };
        }       //^
    """, "\b2")

    fun `test edit macro declaration`() = checkResolvedAndThenUnresolved("""
        macro_rules! foo/*caret*/ {
                    //X
            () => { fn foo() {} };
        }
        foo!();
        //^
    """, "1")

    fun `test edit macro call`() = checkResolvedToXY("""
        macro_rules! foo1 {
                    //X
            () => { fn foo() {} };
        }
        macro_rules! foo2 {
                    //Y
            () => { fn foo() {} };
        }
        foo1/*caret*/!();
        //^
    """, "\b2")

    fun `test edit const expr`() = checkResolvedToXY("""
        struct S<const N: usize>;
        fn foo<
            const N1: usize,
            //X
            const N2: usize
            //Y
        >() {
            let _: S<{ N1/*caret*/ }>;
        }             //^
    """, "\b2")

    private fun checkResolvedToXY(@Language("Rust") code: String, textToType: String) {
        InlineFile(code).withCaret()

        val (refElement, _, offset) = findElementWithDataAndOffsetInEditor<RsWeakReferenceElement>("^")

        refElement.checkResolvedTo("X", offset)

        myFixture.type(textToType)
        PsiDocumentManager.getInstance(project).commitAllDocuments() // process PSI modification events
        check(refElement.isValid)

        refElement.checkResolvedTo("Y", offset)
    }

    private fun checkResolvedToXY(@Language("Rust") code: String, textToType: String, mark: Testmark) = mark.checkHit {
        checkResolvedToXY(code, textToType)
    }

    private fun checkResolvedAndThenUnresolved(@Language("Rust") code: String, textToType: String) {
        InlineFile(code).withCaret()

        val (refElement, _, offset) = findElementWithDataAndOffsetInEditor<RsWeakReferenceElement>("^")

        refElement.checkResolvedTo("X", offset)

        myFixture.type(textToType)
        PsiDocumentManager.getInstance(project).commitAllDocuments() // process PSI modification events
        check(refElement.isValid)

        check(refElement.reference!!.resolve() == null)
    }

    private fun RsWeakReferenceElement.checkResolvedTo(marker: String, offset: Int) {
        val resolved = checkedResolve(offset)
        val target = findElementInEditor<RsNamedElement>(marker)

        check(resolved == target) {
            "$this `${this.text}` should resolve to $target, was $resolved instead"
        }
    }
}
