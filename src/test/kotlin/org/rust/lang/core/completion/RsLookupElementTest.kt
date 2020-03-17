/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.patterns.ElementPattern
import com.intellij.psi.NavigatablePsiElement
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsTupleFieldDecl
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.SimpleScopeEntry
import org.rust.lang.core.types.implLookup

class RsLookupElementTest : RsTestBase() {
    fun `test fn`() = check("""
        fn foo(x: i32) -> Option<String> {}
          //^
    """, tailText = "(x: i32)", typeText = "Option<String>")

    fun `test trait method`() = check("""
        trait T {
            fn foo(&self, x: i32) {}
              //^
        }
    """, tailText = "(&self, x: i32)", typeText = "()")

    fun `test trait by method`() = check("""
        trait T {
            fn foo(&self, x: i32);
        }
        struct S;
        impl T for S {
            fn foo(&self, x: i32) {
              //^
                unimplemented!()
            }
        }
    """, tailText = "(&self, x: i32) of T", typeText = "()")

    fun `test const item`() = check("""
        const C: S = unimplemented!();
            //^
    """, typeText = "S")

    fun `test static item`() = check("""
        static C: S = unimplemented!();
             //^
    """, typeText = "S")

    fun `test tuple struct`() = check("""
        struct S(f32, i64);
             //^
    """, tailText = "(f32, i64)")

    fun `test multi-line tuple struct`() = check("""
        struct S(
             //^
            f32,
            i64
        );
    """, tailText = "(f32, i64)")

    fun `test struct`() = check("""
        struct S { field: String }
             //^
    """, tailText = " { ... }")

    fun `test enum`() = check("""
        enum E { X, Y }
           //^
    """)

    fun `test enum struct variant`() = check("""
        enum E { X {} }
               //^
    """, tailText = " { ... }", typeText = "E")

    fun `test enum tuple variant`() = check("""
        enum E { X(i32, String) }
               //^
    """, tailText = "(i32, String)", typeText = "E")

    fun `test multi-line enum tuple variant`() = check("""
        enum E {
            X(
          //^
                i32,
                String
            )
        }
    """, tailText = "(i32, String)", typeText = "E")

    fun `test named field`() = check("""
        struct S { field: String }
                   //^
    """, typeText = "String", isBold = true)

    fun `test tuple field`() = checkInner<RsTupleFieldDecl>("""
        struct S(String);
                 //^
    """, typeText = "String", isBold = true)

    fun `test macro simple`() = check("""
        macro_rules! test {
            ($ test:expr) => ($ test)
                //^
        }
    """, tailText = null, typeText = "expr")

    fun `test macro definition`() = check("""
        macro_rules! test { () => () }
                     //^
    """, tailText = "!", typeText = null)

    fun `test deprecated fn`() = check("""
        #[deprecated]
        fn foo() {}
          //^
    """, tailText = "()", typeText = "()", isStrikeout = true)

    fun `test deprecated const item`() = check("""
        #[deprecated]
        const C: S = unimplemented!();
            //^
    """, typeText = "S", isStrikeout = true)

    fun `test deprecated enum`() = check("""
        #[deprecated]
        enum E { X, Y }
           //^
    """, isStrikeout = true)

    fun `test mod`() {
        myFixture.configureByText("foo.rs", "")
        val lookup = createLookupElement(
            SimpleScopeEntry("foo", myFixture.file as RsFile),
            RsCompletionContext()
        )
        val presentation = LookupElementPresentation()

        lookup.renderElement(presentation)
        assertNotNull(presentation.icon)
        assertEquals("foo", presentation.itemText)
    }

    fun `test generic field`() = checkProvider("""
        struct S<A> { foo: A }

        fn main() {
            let s = S { foo: 0 };
            s.foo;
             //^
        }
    """, typeText = "i32", isBold = true)

    fun `test generic method (impl)`() = checkProvider("""
        struct S<A>(A);

        impl <B> S<B> {
            fn foo(&self, x: B) -> B { x }
        }

        fn main() {
            let s = S(0);
            s.foo;
             //^
        }
    """, tailText = "(&self, x: i32)", typeText = "i32")

    fun `test generic method (trait)`() = checkProvider("""
        struct S<A>(A);

        trait T<B> {
            fn foo(&self, x: B) -> B { x }
        }

        impl <C> T<C> for S<C> {
        }

        fn main() {
            let s = S(0);
            s.foo;
             //^
        }
    """, tailText = "(&self, x: i32)", typeText = "i32")

    fun `test generic method (impl trait)`() = checkProvider("""
        struct S<A>(A);

        trait T<B> {
            fn foo(&self, x: B) -> B;
        }

        impl <C> T<C> for S<C> {
            fn foo(&self, x: C) -> C { x }
        }

        fn main() {
            let s = S(0);
            s.foo;
             //^
        }
    """, tailText = "(&self, x: i32) of T<i32>", typeText = "i32")

    fun `test generic method (impl trait for reference)`() = checkProvider("""
        struct S<A>(A);

        trait T<B> {
            fn foo(&self, x: B) -> B;
        }

        impl <C> T<C> for &S<C> {
            fn foo(&self, x: C) -> C { x }
        }

        fn main() {
            let s = &S(0);
            s.foo;
             //^
        }

    """, tailText = "(&self, x: i32) of T<i32>", typeText = "i32")

    fun `test generic function (impl)`() = checkProvider("""
        struct S<A>(A);

        impl <B> S<B> {
            fn foo(x: B) -> B { x }
        }

        fn main() {
            S::<i32>::foo;
                     //^
        }
    """, tailText = "(x: i32)", typeText = "i32")

    fun `test generic function (trait)`() = checkProvider("""
        struct S<A>(A);

        trait T<B> {
            fn foo(x: B) -> B { x }
        }

        impl <C> T<C> for S<C> {
        }

        fn main() {
            S::<i32>::foo;
                     //^
        }
    """, tailText = "(x: i32)", typeText = "i32")

    fun `test generic function (impl trait)`() = checkProvider("""
        struct S<A>(A);

        trait T<B> {
            fn foo(x: B) -> B;
        }

        impl <C> T<C> for S<C> {
            fn foo(x: C) -> C { x }
        }

        fn main() {
            S::<i32>::foo;
                     //^
        }
    """, tailText = "(x: i32) of T<i32>", typeText = "i32")

    fun `test const generic function`() = checkProvider("""
        struct S<const N: usize>(i32);

        trait T<const M: usize> {
            fn foo();
        }

        impl <const K: usize> T<{ K }> for S<{ K }> {
            fn foo() {}
        }

        fn main() {
            S::<1>::foo;
                   //^
        }
    """, tailText = "() of T<1>", typeText = "()")

    private fun check(
        @Language("Rust") code: String,
        tailText: String? = null,
        typeText: String? = null,
        isBold: Boolean = false,
        isStrikeout: Boolean = false
    ) = checkInner<RsNamedElement>(code, tailText, typeText, isBold, isStrikeout)

    private inline fun <reified T> checkInner(
        @Language("Rust") code: String,
        tailText: String? = null,
        typeText: String? = null,
        isBold: Boolean = false,
        isStrikeout: Boolean = false
    ) where T : NavigatablePsiElement, T : RsElement {
        InlineFile(code)
        val element = findElementInEditor<T>()
        val lookup = createLookupElement(
            SimpleScopeEntry(element.name!!, element as RsElement),
            RsCompletionContext()
        )

        checkLookupPresentation(
            lookup,
            tailText = tailText,
            typeText = typeText,
            isBold = isBold,
            isStrikeout = isStrikeout
        )
    }

    private fun checkProvider(
        @Language("Rust") code: String,
        tailText: String? = null,
        typeText: String? = null,
        isBold: Boolean = false,
        isStrikeout: Boolean = false
    ) {
        InlineFile(code)
        val element = findElementInEditor<RsReferenceElement>()
        val context = RsCompletionContext(element.implLookup)
        val processedPathNames = mutableSetOf<String>()

        val lookups = mutableListOf<LookupElement>()
        val result = object : CompletionResultSet(PrefixMatcher.ALWAYS_TRUE, null, null) {
            override fun caseInsensitive(): CompletionResultSet = this
            override fun withPrefixMatcher(matcher: PrefixMatcher): CompletionResultSet = this
            override fun withPrefixMatcher(prefix: String): CompletionResultSet = this
            override fun restartCompletionOnPrefixChange(prefixCondition: ElementPattern<String>?) {}
            override fun addLookupAdvertisement(text: String) {}
            override fun withRelevanceSorter(sorter: CompletionSorter): CompletionResultSet = this
            override fun restartCompletionWhenNothingMatches() {}
            override fun addElement(element: LookupElement) {
                lookups.add(element)
            }
        }

        RsCommonCompletionProvider.addCompletionVariants(element, result, context, processedPathNames)
        if (element is RsMethodOrField) {
            RsCommonCompletionProvider.addMethodAndFieldCompletion(element, result, context)
        }

        val lookup = lookups.single {
            val namedElement = it.psiElement as? RsNamedElement
            namedElement?.name == element.referenceName
        }
        checkLookupPresentation(
            lookup,
            tailText = tailText,
            typeText = typeText,
            isBold = isBold,
            isStrikeout = isStrikeout
        )
    }

    private fun checkLookupPresentation(
        lookup: LookupElement,
        tailText: String?,
        typeText: String?,
        isBold: Boolean,
        isStrikeout: Boolean
    ) {
        val presentation = LookupElementPresentation()
        lookup.renderElement(presentation)

        assertNotNull("Item icon should be not null", presentation.icon)
        assertEquals("Tail text mismatch", tailText, presentation.tailText)
        assertEquals("Type text mismatch", typeText, presentation.typeText)
        assertEquals("Bold text attribute mismatch", isBold, presentation.isItemTextBold)
        assertEquals("Strikeout text attribute mismatch", isStrikeout, presentation.isStrikeout)
    }
}
