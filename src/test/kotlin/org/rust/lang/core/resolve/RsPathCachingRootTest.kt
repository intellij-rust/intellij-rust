/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.psi.PsiAnchor
import com.intellij.psi.StubBasedPsiElement
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPathType
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.stubDescendantsOfTypeOrSelf
import org.rust.lang.core.resolve.ref.RsPathReferenceImpl
import org.rust.lang.core.types.rawType
import org.rust.openapiext.toPsiFile

class RsPathCachingRootTest : RsTestBase() {
    fun `test path arguments`() = doTest("""
        struct Foo<A, B>(A, B);
        struct Bar<T>(T);
        struct Baz;
        type T = Foo<Bar<Bar<Bar<Baz>>>, Bar<Bar<Bar<Baz>>>>;
               //X //^ //^ //^ //^     //^ //^ //^ //^
    """)

    fun `test path arguments with qualified path`() = doTest("""
        mod inner {
            pub struct Foo<A, B>(A, B);
            pub struct Bar<T>(T);
            pub struct Baz;
        }
        type T = inner::Foo<inner::Bar<inner::Bar<inner::Bar<inner::Baz>>>, inner::Bar>;
                      //X        //^        //^        //^        //^            //^
    """)

    fun `test expr path arguments`() = doTest("""
        struct Foo<A, B>(A, B);
        struct Bar<T>(T);
        struct Baz;
        fn main() {
            let _ = Foo::<   Bar<Bar<Bar<Baz>>>, Bar<Bar<Bar<Baz>>>>(1, 2);
                       //X //^ //^ //^ //^     //^ //^ //^ //^
        }
    """, testWithStub = false)

    fun `test caching root is null for expr path`() = checkCachingRootIsNull("""
        struct Foo<A, B>(A, B);
        struct Bar<T>(T);
        struct Baz;
        fn main() {
            let _ = Foo::<Bar<Baz>, Bar<Baz>>(1, 2);
                  //^
        }
    """)

    fun `test path arguments in parens`() = doTest("""
        struct Foo<T>(T);
        struct Bar;
        type T = (   Foo<(Foo<(Foo<(Foo<(Bar)>)>)>)>);
               //X //^  //^  //^  //^  //^
    """)

    fun `test tuple`() = doTest("""
        struct Foo<T>(T);
        struct Bar;
        type T = (   Foo<Bar>, Foo<Bar>);
               //X //^ //^   //^ //^
    """)

    fun `test path arguments with tuple`() = doTest("""
        struct Foo<T>(T);
        struct Bar;
        type T = Foo<(Foo<Foo<Foo<(Bar,)>>>, Foo<Foo<Foo<(Bar)>>>)>;
               //X  //^ //^ //^  //^       //^ //^ //^  //^
    """)

    fun `test path arguments with refs`() = doTest("""
        struct Foo<T>(T);
        struct Bar;
        type T = Foo<&Foo<&mut Foo<*const Foo<*mut Foo<Bar>>>>>;
               //X  //^      //^        //^      //^ //^
    """)

    fun `test path arguments with array and slice`() = doTest("""
        struct Foo<T>(T);
        struct Bar;
        type T = Foo<[Foo<[Foo<Foo<Bar>>]>; 1]>;
               //X  //^  //^ //^ //^
    """)

    fun `test path arguments with fn pointer`() = doTest("""
        struct Foo<T>(T);
        struct Bar;
        type T = Foo<fn(Foo<Foo<Foo<Bar>>>, Foo<Bar>) -> Foo<Foo<Foo<Bar>>>>;
               //X    //^ //^ //^ //^     //^ //^      //^ //^ //^ //^
    """)

    fun `test path arguments with trait object`() = doTest("""
        trait Trait<T> {}
        trait Sync {}
        struct Foo<T>(T);
        struct Bar;
        type T = Foo<dyn Trait<Foo<Foo<Foo<Bar>>>> + Sync>;
               //X      //^  //^ //^ //^ //^       //^
    """)

    fun `test fn sugar`() = doTest("""
        #[lang = "fn_once"]
        trait FnOnce<Args> { type Output; }
        struct Foo<T>(T);
        struct Bar;
        type T = Foo<dyn FnOnce(Foo<Foo<Bar>>) -> Foo<Foo<Bar>>>;
               //X     //^    //^ //^ //^       //^ //^ //^
    """)

    fun `test trait object with assoc type binding`() = doTest("""
        trait Trait {
            type Item;
        }
        trait Sync {}
        struct Foo<T>(T);
        struct Bar;
        type T = dyn Trait<Item = Foo<Foo<Foo<Bar>>>> + Sync;
               //X //^          //^ //^ //^ //^       //^
    """)

    fun `test fn signature 1`() = doTest("""
        trait Tr1<T> {}
        trait Tr2<T> {}
        struct Foo<T>(T);
        struct Bar;
        fn foo<A: Tr1<B>, B: Tr1<A>>(_: Foo<Bar>, _: Foo<Bar>) -> Foo<Bar>
            where                     //X //^
                A: Tr2<B>, B: Tr2<A>,
        { let a: Bar; todo!() }
    """)

    fun `test fn signature 2`() = doTest("""
        trait Tr1<T> {}
        trait Tr2<T> {}
        struct Foo<T>(T);
        struct Bar;
        fn foo<A: Tr1<B>, B: Tr1<A>>(_: Foo<Bar>, _: Foo<Bar>) -> Foo<Bar>
            where                                  //X //^
                A: Tr2<B>, B: Tr2<A>,
        { let a: Bar; todo!() }
    """)

    fun `test fn signature 3`() = doTest("""
        trait Tr1<T> {}
        trait Tr2<T> {}
        struct Foo<T>(T);
        struct Bar;
        fn foo<A: Tr1<B>, B: Tr1<A>>(_: Foo<Bar>, _: Foo<Bar>) -> Foo<Bar>
            where                                               //X //^
                A: Tr2<B>, B: Tr2<A>,
        { let a: Bar; todo!() }
    """)

    fun `test impl signature 1`() = doTest("""
        trait Tr1<T> {}
        trait Tr2<T> {}
        struct Foo<T>(T);
        impl<A: Tr1<B>, B: Tr1<A>> Trait<A> for Foo<Foo<B>>
            where                //X   //^
                A: Tr2<B>, B: Tr2<A>,
        {}
    """)

    fun `test impl signature 2`() = doTest("""
        trait Tr1<T> {}
        trait Tr2<T> {}
        struct Foo<T>(T);
        impl<A: Tr1<B>, B: Tr1<A>> Trait<A> for Foo<Foo<B>>
            where                             //X //^ //^
                A: Tr2<B>, B: Tr2<A>,
        {}
    """)

    private fun doTest(@Language("Rust") code: String, testWithStub: Boolean = true) {
        val trimmedCode = code.trimIndent()
        InlineFile(trimmedCode)
        val paths = findElementsWithDataAndOffsetInEditor<RsPath>().map { it.first }
        val root = findElementAndDataInEditor<RsElement>("X").first
        doTestWithRootAndPaths(root, paths)

        if (testWithStub) {
            checkAstNotLoaded { true }
            val file = myFixture.createFile("lib.rs", trimmedCode).toPsiFile(project) as RsFile
            val fileStub = file.stubTree!!
            val stubPaths = paths.map { fileStub.spine.getStubPsi(PsiAnchor.calcStubIndex(it)) as RsPath }
            val stubRoot = fileStub.spine.getStubPsi(PsiAnchor.calcStubIndex(root as StubBasedPsiElement<*>))
                as RsElement
            doTestWithRootAndPaths(stubRoot, stubPaths)
        }
    }

    private fun doTestWithRootAndPaths(rawRoot: RsElement, rawPaths: List<RsPath>) {
        val paths = rawPaths.toMutableList()
        val root = if (rawRoot is RsPath && rawRoot.parent is RsPathType) {
            paths += rawRoot
            rawRoot.parent as RsElement
        } else {
            rawRoot
        }
        for (path in paths) {
            val actualRoot = RsPathReferenceImpl.getRootCachingElement(path)
            check(root == actualRoot) {
                "Unexpected caching root for element `${path.text}`. Expected `${root.text}`, got `${actualRoot?.text}`"
            }
        }
        val pathSet = paths.toMutableSet()
        if (root is RsPath && root !in pathSet) {
            val actualRoot = RsPathReferenceImpl.getRootCachingElement(root)
            check(root == actualRoot) {
                "Unexpected caching root for the root itself. Expected `${root.text}`, got `${actualRoot?.text}`"
            }
            pathSet += root
        }
        val collectedPaths = RsPathReferenceImpl.collectNestedPathsFromRoot(root).toSet()
        if (pathSet != collectedPaths) {
            assertEquals(pathSet.joinToString("\n") { it.text }, collectedPaths.joinToString("\n") { it.text })
        }

        val collectedPaths2 = root.stubDescendantsOfTypeOrSelf<RsPath>()
            .filter { RsPathReferenceImpl.getRootCachingElement(it) == root }
            .toSet()
        if (pathSet != collectedPaths2) {
            assertEquals(pathSet.joinToString("\n") { it.text }, collectedPaths2.joinToString("\n") { it.text })
        }

        if (root is RsTypeReference) {
            root.rawType
        }
    }

    private fun checkCachingRootIsNull(@Language("Rust") code: String) {
        InlineFile(code)
        val path = findElementInEditor<RsPath>()
        assertNull(RsPathReferenceImpl.getRootCachingElement(path))
    }
}
