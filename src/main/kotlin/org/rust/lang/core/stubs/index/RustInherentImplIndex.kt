package org.rust.lang.core.stubs.index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.types.RustType
import org.rust.lang.core.types.util.resolvedType

class RustInherentImplIndex : StringStubIndexExtension<RustImplItemElement>() {
    override fun getKey(): StubIndexKey<String, RustImplItemElement> = KEY

    companion object {
        val KEY: StubIndexKey<String, RustImplItemElement> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustInherentImplIndex")

        fun getInherentImpls(project: Project, type: RustType): Collection<RustImplItemElement> {
            val baseTypeName = type.baseTypeName ?: return emptyList()
            // XXX: can't use more effective `processElements` here, because it is impossible to
            // process several indexes simultaneously, and we process `RustModulesIndex`
            // during resolve.
            val candidates = StubIndex.getElements(KEY, baseTypeName, project, GlobalSearchScope.allScope(project), RustImplItemElement::class.java)

            return candidates.filter { it.type?.resolvedType == type }
        }

    }
}
