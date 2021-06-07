package org.rust.ide.navigation.goto

import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.psi.PsiElement

// BACKCOMPAT: 2021.1. Merge into `RsGotoImplementationsTestBase`
class RsGotoImplementationsTest : RsGotoImplementationsTestBase() {

    fun `test multiple targets for method impl`() = doMultipleTargetsTest("""
        struct Foo;
        struct Bar<T>(T);
        struct Baz<T> { x: T }
        trait Trait {
            fn bar/*caret*/();
        }

        impl Trait for Foo {
            fn bar() { todo!() }
        }
        impl<T> Trait for Bar<T> {
            fn bar() { todo!() }
        }
        impl<T> Trait for Baz<T> where T : Clone {
            fn bar() { todo!() }
        }
    """,
        "Trait for Foo test_package",
        "Trait for Bar<T> test_package",
        "Trait for Baz<T> where T: Clone test_package"
    )

    fun `test multiple targets for trait impl`() = doMultipleTargetsTest("""
        struct Foo;
        struct Bar<T>(T);
        struct Baz<T> { x: T }
        trait Trait/*caret*/ {
            fn bar();
        }

        impl Trait for Foo {
            fn bar() { todo!() }
        }
        impl<T> Trait for Bar<T> {
            fn bar() { todo!() }
        }
        impl<T> Trait for Baz<T> where T : Clone {
            fn bar() { todo!() }
        }
    """,
        "Trait for Foo test_package",
        "Trait for Bar<T> test_package",
        "Trait for Baz<T> where T: Clone test_package"
    )

    override fun GotoTargetHandler.GotoData.render(element: PsiElement): String = getComparingObject(element)
}
