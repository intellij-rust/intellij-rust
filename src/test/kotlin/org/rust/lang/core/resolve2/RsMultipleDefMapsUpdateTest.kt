/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import org.junit.Assert
import org.rust.*
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.rustStructureModificationTracker
import org.rust.lang.core.resolve2.CrateInfo.*

/** Tests which exactly [CrateDefMap]s are updated when we modify some crate and then run resolve in some other crate */
@ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
class RsMultipleDefMapsUpdateTest : RsTestBase() {

    override fun setUp() {
        super.setUp()
        fileTreeFromText(codeAllCrates).create()
        project.getAllDefMaps()
    }

    private fun modifyCrates(vararg crates: CrateInfo) {
        runWriteCommandAction(project) {
            for (crateToModify in crates) {
                val file = crateToModify.crate.rootMod!!
                val modificationCount = project.rustStructureModificationTracker.modificationCount
                file.add(RsPsiFactory(project).createFunction("pub fn foo$modificationCount() {}"))
            }
        }
    }

    private fun getDefMap(crateInfo: CrateInfo, cratesUpdatedExpected: Array<CrateInfo>) {
        val crates = project.crateGraph.topSortedCrates
        val crateIds = crates.mapNotNull { it.id }

        val getDefMapStamps = {
            crateIds.associateWith {
                val holder = project.defMapService.getDefMapHolder(it)
                holder.defMap!!.timestamp
            }
        }

        val defMapStampsOld = getDefMapStamps()
        project.defMapService.getOrUpdateIfNeeded(crateInfo.crate.id!!)!!
        val defMapStampsNew = getDefMapStamps()

        val cratesUpdatedActual = crateIds
            .filter { defMapStampsOld[it] != defMapStampsNew[it] }
            .map { id -> values().single { it.crate.id == id } }
            .sorted()
        Assert.assertEquals(cratesUpdatedExpected.sorted(), cratesUpdatedActual)
    }

    private val CrateInfo.crate: Crate
        get() {
            val crateRoot = myFixture.findFileInTempDir(crateRootPath)
            return project.crateGraph.findCrateByRootMod(crateRoot)!!
        }

    fun `test no modifications`() {
        getDefMap(BIN, emptyArray())
        getDefMap(LIB, emptyArray())
    }

    fun `test modify binary crate`() {
        modifyCrates(BIN)
        getDefMap(LIB, emptyArray())
        getDefMap(BIN, arrayOf(BIN))
        getDefMap(BIN, emptyArray())

        modifyCrates(BIN)
        getDefMap(LIB, emptyArray())
        getDefMap(BIN, arrayOf(BIN))
    }

    fun `test modify trans-lib and trans-lib-2 crates`() {
        modifyCrates(TRANS_LIB2)
        getDefMap(DEP_LIB, arrayOf(DEP_LIB, TRANS_LIB, TRANS_LIB2))
        modifyCrates(TRANS_LIB)
        getDefMap(LIB, arrayOf(LIB, DEP_LIB, TRANS_LIB))
    }

    fun `test modify trans-lib-2 crate 1`() {
        modifyCrates(TRANS_LIB2)
        getDefMap(TRANS_LIB2, arrayOf(TRANS_LIB2))
        getDefMap(TRANS_LIB, arrayOf(TRANS_LIB))
        getDefMap(DEP_LIB, arrayOf(DEP_LIB))
        getDefMap(LIB, arrayOf(LIB))
        getDefMap(BIN, arrayOf(BIN))
    }

    fun `test modify trans-lib-2 crate 2`() {
        modifyCrates(TRANS_LIB2)
        getDefMap(TRANS_LIB, arrayOf(TRANS_LIB, TRANS_LIB2))
        getDefMap(BIN, arrayOf(BIN, LIB, DEP_LIB))
    }

    fun `test modify dep-lib-2 and trans-lib-2 crates 1`() {
        modifyCrates(DEP_LIB2, TRANS_LIB2)
        getDefMap(DEP_LIB, arrayOf(DEP_LIB, DEP_LIB2, TRANS_LIB, TRANS_LIB2))
        getDefMap(DEP_LIB, emptyArray())
    }

    fun `test modify dep-lib-2 and trans-lib-2 crates 2`() {
        modifyCrates(DEP_LIB2, TRANS_LIB2)
        getDefMap(DEP_LIB2, arrayOf(DEP_LIB2))
        getDefMap(TRANS_LIB, arrayOf(TRANS_LIB, TRANS_LIB2))
        getDefMap(DEP_LIB, arrayOf(DEP_LIB))
        getDefMap(DEP_LIB, emptyArray())
    }
}

/**
 * See [WithDependencyRustProjectDescriptor.testCargoProject]
 *
 * BIN -> LIB -> DEP_LIB -> TRANS_LIB -> TRANS_LIB2
 *                  |
 *                  +-----> DEP_LIB2
 */
private enum class CrateInfo(val crateRootPath: String) {
    BIN("main.rs"),
    LIB("lib.rs"),
    DEP_LIB("dep-lib/lib.rs"),
    DEP_LIB2("dep-lib-new/lib.rs"),
    TRANS_LIB("trans-lib/lib.rs"),
    TRANS_LIB2("trans-lib-2/lib.rs"),
}

// crate roots are empty files
private const val codeAllCrates: String = """
//- main.rs
//- lib.rs
//- dep-lib/lib.rs
//- dep-lib-new/lib.rs
//- trans-lib/lib.rs
//- trans-lib-2/lib.rs
"""
