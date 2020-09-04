/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.ide.intentions.ImplTraitToTypeParamIntention.Companion.outerImplTestMark

class ImplTraitToTypeParamIntentionTest : RsIntentionTestBase(ImplTraitToTypeParamIntention::class) {
    fun `test simple`() = doAvailableTest("""
        trait Trait{}
        fn test(arg: impl/*caret*/ Trait) {}
    """, """
        trait Trait{}
        fn test<T: Trait>(arg: T) {}
    """)

    fun `test nested inner`() = doAvailableTest("""
        trait Trait<T>{}
        fn test(arg: impl Trait<impl/*caret*/ Trait>) {}
    """, """
        trait Trait<T>{}
        fn test<T: Trait>(arg: impl Trait<T>) {}
    """)

    fun `test with existing type parameter list`() = doAvailableTest("""
        trait Trait<T>{}
        fn test<X: Trait>(arg: impl/*caret*/ Trait<X>) {}
    """, """
        trait Trait<T>{}
        fn test<X: Trait, T: Trait<X>>(arg: T) {}
    """)

    fun `test nested outer`() = doAvailableTest("""
        trait Trait<T>{}
        fn test(arg: impl/*caret*/ Trait<impl Trait>) {}
    """, """
        trait Trait<T>{}
        fn test(arg: impl Trait<impl Trait>) {}
    """, outerImplTestMark)

    fun `test with lifetime`() = doAvailableTest("""
        trait Trait{}
        fn test<'a>(arg: impl/*caret*/ Trait + 'a) {}
    """, """
        trait Trait{}
        fn test<'a, T: Trait + 'a>(arg: T) {}
    """)

    fun `test unavailable in return`() = doUnavailableTest("""
        trait Trait{}
        fn test() -> impl/*caret*/ Trait {}
    """)
}
