/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.project.Project
import org.intellij.lang.annotations.Language
import org.rust.ide.intentions.addTraitImpl.AddTraitImplIntention
import org.rust.ide.refactoring.implementMembers.RsTraitMemberChooserMember
import org.rust.ide.refactoring.implementMembers.TraitMemberChooser
import org.rust.ide.refactoring.implementMembers.withMockTraitMemberChooser

class AddTraitImplIntentionTest : RsIntentionTestBase(AddTraitImplIntention::class) {
    fun `test empty trait`() = doTest("""
        trait Trait {}

        struct S/*caret*/;
    """, """
        trait Trait {}

        struct S;

        impl Trait for S {}
    """, "Trait")

    fun `test generic struct`() = doTest("""
        trait Trait {}

        struct S<'a, R, T>(R, &'a T)/*caret*/;
    """, """
        trait Trait {}

        struct S<'a, R, T>(R, &'a T);

        impl<'a, R, T> Trait for S<'a, R, T> {}
    """, "Trait")

    fun `test generic trait`() = doTest("""
        trait Trait<R, T> {}

        struct S/*caret*/;
    """, """
        trait Trait<R, T> {}

        struct S;

        impl<R, T> Trait<R, T> for S {}
    """, "Trait")

    fun `test generic struct and trait`() = doTest("""
        trait Trait<A, B> {}

        struct S<'a, R, T>(R, &'a T)/*caret*/;
    """, """
        trait Trait<A, B> {}

        struct S<'a, R, T>(R, &'a T);

        impl<'a, R, T> Trait<> for S<'a, R, T> {}
    """, "Trait")

    fun `test import trait from module`() = doTest("""
        mod foo {
            pub trait Trait {}
        }

        struct S/*caret*/;
    """, """
        use foo::Trait;

        mod foo {
            pub trait Trait {}
        }

        struct S;

        impl Trait for S {}
    """, "foo::Trait")

    fun `test implement all supertraits`() = doTest("""
        trait Trait3 {}
        trait Trait2: Trait3 {}
        trait Trait1: Trait2 {}

        struct S/*caret*/;
    """, """
        trait Trait3 {}
        trait Trait2: Trait3 {}
        trait Trait1: Trait2 {}

        struct S;

        impl Trait3 for S {}

        impl Trait2 for S {}

        impl Trait1 for S {}
    """, "Trait1")

    fun `test implement each supertrait only once`() = doTest("""
        trait Trait3 {}
        trait Trait2: Trait3 {}
        trait Trait1: Trait2 + Trait3 {}

        struct S/*caret*/;
    """, """
        trait Trait3 {}
        trait Trait2: Trait3 {}
        trait Trait1: Trait2 + Trait3 {}

        struct S;

        impl Trait3 for S {}

        impl Trait2 for S {}

        impl Trait1 for S {}
    """, "Trait1")

    fun `test do not implement already implemented traits`() = doTest("""
        trait Trait3 {}
        trait Trait2: Trait3 {}
        trait Trait1: Trait2 + Trait3 {}

        struct S/*caret*/;

        impl Trait2 for S {}
    """, """
        trait Trait3 {}
        trait Trait2: Trait3 {}
        trait Trait1: Trait2 + Trait3 {}

        struct S/*caret*/;

        impl Trait3 for S {}

        impl Trait1 for S {}

        impl Trait2 for S {}
    """, "Trait1")

    fun `test implement all trait items`() = doTestWithMemberSelection("""
        trait Trait {
            const FOO: u32;
            fn foo(&self);
        }

        struct S/*caret*/;
    """, """
        trait Trait {
            const FOO: u32;
            fn foo(&self);
        }

        struct S;

        impl Trait for S {
            const FOO: u32 = 0;

            fn foo(&self) {
                unimplemented!()
            }
        }
    """, "Trait", "foo", "FOO")

    fun `test implement selected trait items`() = doTestWithMemberSelection("""
        trait Trait {
            const FOO: u32;
            fn foo(&self);
        }

        struct S/*caret*/;
    """, """
        trait Trait {
            const FOO: u32;
            fn foo(&self);
        }

        struct S;

        impl Trait for S {
            fn foo(&self) {
                unimplemented!()
            }
        }
    """, "Trait", "foo")

    private fun doTest(@Language("Rust") before: String, @Language("Rust") after: String, fragmentText: String) {
        AddTraitImplIntention.PATH_FRAGMENT_TEXT = fragmentText
        doAvailableTest(before, after)
    }

    private fun doTestWithMemberSelection(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        fragmentText: String,
        vararg selectedItems: String
    ) {
        withMockTraitMemberChooser(object : TraitMemberChooser {
            override fun invoke(
                project: Project,
                all: List<RsTraitMemberChooserMember>,
                selectedByDefault: List<RsTraitMemberChooserMember>
            ): List<RsTraitMemberChooserMember> = all.filter { it.member.name in selectedItems }
        }) {
            doTest(before, after, fragmentText)
        }
    }
}
