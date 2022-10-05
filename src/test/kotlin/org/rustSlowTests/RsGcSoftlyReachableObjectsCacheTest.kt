/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import com.intellij.util.ref.GCUtil
import org.rust.RsTestBase
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.crate.impl.CrateGraphServiceImpl
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.ext.withSubst
import org.rust.lang.core.resolve.RsImplIndexAndTypeAliasCache
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.TyFingerprint
import org.rust.lang.core.types.implLookup

/**
 * Some tests that involve [GCUtil.tryGcSoftlyReachableObjects] invocation
 */
class RsGcSoftlyReachableObjectsCacheTest : RsTestBase() {
    /**
     * Issue https://github.com/intellij-rust/intellij-rust/issues/9432
     *
     * A kind of test for [org.rust.lang.core.crate.impl.CargoBasedCrate.equals] and
     * [org.rust.lang.core.crate.hasTransitiveDependencyOrSelf].
     */
    fun `test trait selection works after GC collects the crate graph object`() {
        InlineFile("""
            struct S;
            trait Trait {}
            impl Trait for S {}
        """.trimIndent())

        val structTy = myFixture.file.descendantsOfType<RsStructItem>().single().declaredType
        val trait = myFixture.file.descendantsOfType<RsTraitItem>().single()

        // Retrieve impl in order to retain it in the memory and in the cache
        val impls = RsImplIndexAndTypeAliasCache.getInstance(project)
            .findPotentialImpls(TyFingerprint.create(structTy)!!)

        fun canSelectImpl(): Boolean =
            trait.implLookup.canSelect(TraitRef(structTy, trait.withSubst()))

        check(canSelectImpl())
        GCUtil.tryGcSoftlyReachableObjects { !(project.crateGraph as CrateGraphServiceImpl).hasUpToDateGraph() }
        check(canSelectImpl())

        check(impls.isNotEmpty()) // use `impls` in order to retain it in the memory until this moment
    }
}
