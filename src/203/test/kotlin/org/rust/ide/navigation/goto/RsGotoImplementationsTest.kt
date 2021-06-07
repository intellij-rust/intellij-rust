package org.rust.ide.navigation.goto

import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.psi.PsiElement

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
        "Trait for Foo (in test_package)",
        "Trait for Bar<T> (in test_package)",
        "Trait for Baz<T> where T: Clone (in test_package)"
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
        "Trait for Foo (in test_package)",
        "Trait for Bar<T> (in test_package)",
        "Trait for Baz<T> where T: Clone (in test_package)"
    )

    override fun GotoTargetHandler.GotoData.render(element: PsiElement): String {
        val renderer = GotoTargetHandler.createRenderer(this, element) ?: defaultRenderer
        return renderer.getComparingObject(element).toString()
    }

    companion object {
        private val defaultRenderer: PsiElementListCellRenderer<PsiElement> by lazy {
            val clazz = Class.forName("com.intellij.codeInsight.navigation.GotoTargetHandler\$DefaultPsiElementListCellRenderer")
            val constructor = clazz.getDeclaredConstructor()
            constructor.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            constructor.newInstance() as PsiElementListCellRenderer<PsiElement>
        }
    }
}
