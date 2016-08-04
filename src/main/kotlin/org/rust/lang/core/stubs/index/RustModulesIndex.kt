package org.rust.lang.core.stubs.index

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.RustFileElementType
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.RustModDeclItemElement
import org.rust.lang.core.psi.containingMod
import org.rust.lang.core.psi.impl.RustFile

class RustModulesIndex : StringStubIndexExtension<RustModDeclItemElement>() {

    override fun getVersion(): Int = RustFileElementType.stubVersion

    override fun getKey(): StubIndexKey<String, RustModDeclItemElement> = KEY

    companion object {
        val KEY: StubIndexKey<String, RustModDeclItemElement> = StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustModulesIndex")

        fun getSuperFor(mod: RustFile): RustMod? {
            val project = mod.project
            val key = mod.modName ?: return null

            var result: RustMod? = null

            StubIndex.getInstance().processElements(
                KEY, key, project, GlobalSearchScope.allScope(project), RustModDeclItemElement::class.java
            ) { modDecl ->
                if (modDecl.reference?.resolve() == mod) {
                    result = modDecl.containingMod
                    false
                } else {
                    true
                }
            }

            return result
        }
    }
}
