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
    private fun RsWeakReferenceElement.checkResolvedTo(marker: String, offset: Int) {
        val resolved = checkedResolve(offset)
        val target = findElementInEditor<RsNamedElement>(marker)

        check(resolved == target) {
            "$this `${this.text}` should resolve to $target, was $resolved instead"
        }
    }

    private fun doTest(@Language("Rust") code: String, textToType: String) {
        InlineFile(code).withCaret()

        val (refElement, _, offset) = findElementWithDataAndOffsetInEditor<RsWeakReferenceElement>("^")

        refElement.checkResolvedTo("X", offset)

        myFixture.type(textToType)
        PsiDocumentManager.getInstance(project).commitAllDocuments() // process PSI modification events
        check(refElement.isValid)

        refElement.checkResolvedTo("Y", offset)
    }

    private fun doTest(@Language("Rust") code: String, textToType: String, mark: Testmark) = mark.checkHit {
        doTest(code, textToType)
    }

    fun `test cache invalidated on rust structure change`() = doTest("""
        mod a { struct S; }
                     //X
        mod b { struct S; }
                     //Y
        use a/*caret*/::S;
        type T = S;
               //^
    """, "\bb", RsResolveCache.Testmarks.cacheCleared)

    fun `test resolve correctly without global cache invalidation`() = doTest("""
        struct S1;
             //X
        struct S2;
             //Y
        fn main() {
            let a: S1/*caret*/;
        }        //^
    """, "\b2")
}
