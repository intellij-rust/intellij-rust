/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.psi.PsiDocumentManager
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsWeakReferenceElement
import org.rust.lang.core.resolve.ref.RsResolveCache
import org.rust.openapiext.Testmark

class RsResolveCacheTest : RsTestBase() {
    fun `test cache invalidated on rust structure change`() = checkResolvedToXY("""
        mod a { struct S; }
                     //X
        mod b { struct S; }
                     //Y
        use a/*caret*/::S;
        type T = S;
               //^
    """, "\bb", RsResolveCache.Testmarks.cacheCleared)

    fun `test resolve correctly without global cache invalidation`() = checkResolvedToXY("""
        struct S1;
             //X
        struct S2;
             //Y
        fn main() {
            let a: S1/*caret*/;
        }        //^
    """, "\b2")

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

    fun `test edit local lifetime declaration`() = checkResolvedAndThenUnresolved("""
        fn main() {
            let _: &dyn for<'lifetime/*caret*/>
                            //X
                Trait<'lifetime>;
        }               //^
    """, "1")

    fun `test edit fn-signature lifetime declaration`() = checkResolvedAndThenUnresolved("""
        fn foo<'lifetime/*caret*/>() {
                //X
            let _: &dyn Trait<'lifetime>;
        }                     //^
    """, "1")

    fun `test macro meta variable reference`() = checkResolvedAndThenUnresolved("""
        macro_rules! foo {
            ($ item_var/*caret*/:item) => {
                //X
                $ item_var
            };    //^
        }
    """, "1")

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
