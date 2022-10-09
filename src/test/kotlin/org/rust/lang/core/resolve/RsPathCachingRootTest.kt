/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.descendantsOfTypeOrSelf
import org.rust.lang.core.resolve.ref.RsPathReferenceImpl

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

    // TODO move the caching root upper
    fun `test expr path arguments 1`() = doTest("""
        struct Foo<A, B>(A, B);
        struct Bar<T>(T);
        struct Baz;
        fn main() {
            let _ = Foo::<Bar<Bar<Bar<Baz>>>, Bar<Bar<Bar<Baz>>>>(1, 2);
                        //X //^ //^ //^
        }
    """)

    fun `test expr path arguments 2`() = doTest("""
        struct Foo<A, B>(A, B);
        struct Bar<T>(T);
        struct Baz;
        fn main() {
            let _ = Foo::<Bar<Bar<Bar<Baz>>>, Bar<Bar<Bar<Baz>>>>(1, 2);
                                            //X //^ //^ //^
        }
    """)

    fun `test path arguments in parens`() = doTest("""
        struct Foo<T>(T);
        struct Bar;
        type T = (Foo<(Foo<(Foo<(Foo<(Bar)>)>)>)>);
                //X  //^  //^  //^  //^
    """)

    // TODO move the caching root upper
    fun `test tuple`() = doTest("""
        struct Foo<T>(T);
        struct Bar;
        type T = (Foo<Bar>, Foo<Bar>);
                //X //^
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

    // TODO move the caching root upper
    fun `test path arguments with fn pointer`() = doTest("""
        struct Foo<T>(T);
        struct Bar;
        type T = Foo<fn(Foo<Foo<Foo<Bar>>>) -> Foo<Foo<Foo<Bar>>>>;
                      //X //^ //^ //^
    """)

    // TODO move the caching root upper
    fun `test path arguments with trait object`() = doTest("""
        trait Trait<T> {}
        trait Sync {}
        struct Foo<T>(T);
        struct Bar;
        type T = Foo<dyn Trait<Foo<Foo<Foo<Bar>>>> + Sync>;
                        //X  //^ //^ //^ //^
    """)

    // TODO move the caching root upper
    fun `test fn sugar`() = doTest("""
        #[lang = "fn_once"]
        trait FnOnce<Args> { type Output; }
        struct Foo<T>(T);
        struct Bar;
        type T = dyn FnOnce(Foo<Foo<Bar>>) -> Foo<Foo<Bar>>;
                          //X //^ //^
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

    private fun doTest(@Language("Rust") code: String) {
        InlineFile(code)
        val paths = findElementsWithDataAndOffsetInEditor<RsPath>().map { it.first }
        val root = findElementAndDataInEditor<RsElement>("X").first
        for (path in paths) {
            assertEquals(root, RsPathReferenceImpl.getRootCachingElement(path))
        }
        val pathSet = paths.toMutableSet()
        if (root is RsPath && root !in pathSet) {
            assertEquals(root, RsPathReferenceImpl.getRootCachingElement(root))
            pathSet += root
        }
        val collectedPaths = RsPathReferenceImpl.collectNestedPathsFromRoot(root)
        assertSameElements(pathSet, collectedPaths)

        val collectedPaths2 = root.descendantsOfTypeOrSelf<RsPath>()
            .filter { RsPathReferenceImpl.getRootCachingElement(it) == root }

        assertSameElements(pathSet, collectedPaths2)
    }
}
