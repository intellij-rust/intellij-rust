package org.rust.lang.core.core

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import org.rust.lang.RustTestCaseBase
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.stubs.index.RustNamedElementIndex

class RustStubAccessTest : RustTestCaseBase() {
    override val dataPath: String get() = ""

    fun testPresentationDoesNotNeedAst() {
        myFixture.addFileToProject("src/main.rs",
            //language=RUST
            """
            fn main(param: i32) {}

            #[test] fn bar() {}

            pub struct S {
                pub f: f32,
                g: u32
            }

            trait T {
                fn foo(self);
                fn bar(self) {}
                fn new() -> Self;
            }

            impl T for S {
                fn foo(self) {}
            }

            pub mod bar;
        """)

        processStubsWithoutAstAccess { element ->
            element.getIcon(0)
            element.getIcon(Iconable.ICON_FLAG_VISIBILITY)
            (element as PsiNamedElement).name
            element.presentation?.let {
                it.locationString
                it.presentableText
                it.getIcon(false)
            }
        }
    }

    fun processStubsWithoutAstAccess(block: (StubBasedPsiElementBase<*>) -> Unit) {
        val psiManager = myFixture.psiManager as PsiManagerImpl
        psiManager.setAssertOnFileLoadingFilter(VirtualFileFilter.ALL, myTestRootDisposable)
        val processed = StubIndex.getInstance()
            .getAllKeys(RustNamedElementIndex.KEY, project)
            .flatMap {
                StubIndex.getElements(
                    RustNamedElementIndex.KEY,
                    it,
                    project,
                    GlobalSearchScope.allScope(project),
                    RustNamedElement::class.java
                )
            }
            .map { block(it as StubBasedPsiElementBase<*>) }
            .count()

        check(processed > 10)
    }
}

