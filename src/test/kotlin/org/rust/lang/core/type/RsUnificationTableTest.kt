/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import junit.framework.TestCase
import org.rust.lang.core.types.infer.UnificationTable
import org.rust.lang.core.types.ty.TyInfer

class RsUnificationTableTest : TestCase() {

    private object Value

    private val table: UnificationTable<TyInfer.IntVar, Value> = UnificationTable()

    private fun checkDifferentSnapshotStates(action: (TyInfer.IntVar) -> Unit) {
        fun checkWithoutSnapshot(action: (TyInfer.IntVar) -> Unit): TyInfer.IntVar {
            val ty = TyInfer.IntVar()
            action(ty)
            check(table.findValue(ty) == Value)
            return ty
        }

        fun checkSnapshotCommit(action: () -> TyInfer.IntVar) {
            val snapshot = table.startSnapshot()
            val ty = action()
            snapshot.commit()
            check(table.findValue(ty) == Value)
        }

        fun checkSnapshotRollback(action: () -> TyInfer.IntVar) {
            val snapshot = table.startSnapshot()
            val ty = action()
            snapshot.rollback()
            check(table.findValue(ty) == null)
        }

        checkWithoutSnapshot(action)
        checkSnapshotCommit { checkWithoutSnapshot(action) }
        checkSnapshotRollback { checkWithoutSnapshot(action) }

        checkSnapshotCommit { // snapshot-snapshot-commit-commit
            checkWithoutSnapshot { ty ->
                table.unifyVarValue(ty, Value)
                checkSnapshotCommit { checkWithoutSnapshot(action) }
            }
        }

        checkSnapshotCommit { // snapshot-snapshot-rollback-commit
            checkWithoutSnapshot { ty ->
                table.unifyVarValue(ty, Value)
                checkSnapshotRollback { checkWithoutSnapshot(action) }
            }
        }

        checkSnapshotRollback { // snapshot-snapshot-commit-rollback
            checkWithoutSnapshot { ty ->
                table.unifyVarValue(ty, Value)
                checkSnapshotCommit { checkWithoutSnapshot(action) }
            }
        }

        checkSnapshotRollback { // snapshot-snapshot-rollback-rollback
            checkWithoutSnapshot { ty ->
                table.unifyVarValue(ty, Value)
                checkSnapshotRollback { checkWithoutSnapshot(action) }
            }
        }
    }

    private fun vars(num: Int): List<TyInfer.IntVar> =
        generateSequence { TyInfer.IntVar() }.take(num).toList()

    fun `test unifyVarValue`() = checkDifferentSnapshotStates { a ->
        table.unifyVarValue(a, Value)
    }

    fun `test unifyVarVar first`() = checkDifferentSnapshotStates { a ->
        val b = TyInfer.IntVar()
        table.unifyVarVar(a, b)
        table.unifyVarValue(b, Value)
    }

    fun `test unifyVarVar last`() = checkDifferentSnapshotStates { a ->
        val b = TyInfer.IntVar()
        table.unifyVarValue(b, Value)
        table.unifyVarVar(a, b)
    }

    fun `test 3 vars`() = checkDifferentSnapshotStates { a ->
        val (b, c) = vars(2)
        table.unifyVarVar(a, b)
        table.unifyVarVar(b, c)
        table.unifyVarValue(c, Value)
    }

    fun `test clusters`() = checkDifferentSnapshotStates { a ->
        val (b, c, d) = vars(3)

        table.unifyVarVar(a, b)
        table.unifyVarVar(c, d)
        table.unifyVarValue(d, Value)

        table.unifyVarVar(b, c)
    }
}
