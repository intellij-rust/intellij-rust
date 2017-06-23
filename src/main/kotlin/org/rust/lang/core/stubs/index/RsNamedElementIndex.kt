package org.rust.lang.core.stubs.index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.lang.core.psi.ext.containingMod
import org.rust.lang.core.resolve.STD_DERIVABLE_TRAITS
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.utils.findWithCache
import org.rust.lang.utils.getElements

class RsNamedElementIndex : StringStubIndexExtension<RsNamedElement>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsNamedElement> = KEY

    companion object {
        val KEY: StubIndexKey<String, RsNamedElement> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustNamedElementIndex")

        fun findDerivableTraits(project: Project, target: String): Collection<RsTraitItem> =
            findWithCache(project, target) { project, target ->
                val stdTrait = STD_DERIVABLE_TRAITS[target]
                getElements(KEY, target, project, GlobalSearchScope.allScope(project))
                    .mapNotNull { it as? RsTraitItem }
                    .filter { e ->
                        if (stdTrait == null) return@filter true
                        e.containingCargoPackage?.origin == PackageOrigin.STDLIB && e.containingMod?.modName == stdTrait.modName
                    }
            }
    }
}
