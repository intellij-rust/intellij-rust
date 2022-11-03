/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils.import

import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.psi.escapeIdentifierIfNeeded
import org.rust.lang.core.psi.ext.RsQualifiedNamedElement

sealed class ImportInfo {

    abstract val usePath: String

    class LocalImportInfo(override val usePath: String) : ImportInfo()

    class ExternCrateImportInfo(
        val crate: Crate,
        val externCrateName: String,
        val needInsertExternCrateItem: Boolean,
        /**
         * Relative depth of importing path's module to module with extern crate item.
         * Used for creation of relative use path.
         *
         * For example, in the following case
         * ```rust
         * // lib.rs from bar crate
         * pub struct Bar {}
         * ```
         *
         * ```rust
         * // main.rs from our crate
         * mod foo {
         *     extern crate bar;
         *     mod baz {
         *          fn f(bar: Bar/*caret*/) {}
         *     }
         * }
         * ```
         *
         * relative depth of path `Bar` is `1`, so we should add `self::` prefix to use path.
         *
         * Can be null if extern crate item is absent or it is in crate root.
         */
        val depth: Int?,
        crateRelativePath: String,
        hasModWithSameNameAsExternCrate: Boolean = false,
    ) : ImportInfo() {
        override val usePath: String = run {
            val absolutePrefix = if (hasModWithSameNameAsExternCrate) "::" else ""
            val externCrateName = externCrateName.escapeIdentifierIfNeeded()
            "$absolutePrefix$externCrateName::$crateRelativePath"
        }
    }
}

class ImportCandidate(
    val item: RsQualifiedNamedElement,
    /**
     * First segment is crate name (can be "crate").
     * Last segment is item name.
     */
    val path: Array<String>,
    /**
     * Corresponds to `path.first()`.
     * May differ from `item.containingCrate`
     */
    val crate: Crate,

    val info: ImportInfo,
    private val isRootPathResolved: Boolean,
): Comparable<ImportCandidate> {

    val itemName: String
        get() = path.last()

    override fun compareTo(other: ImportCandidate): Int =
        COMPARATOR.compare(this, other)

    companion object {
        private val COMPARATOR: Comparator<ImportCandidate> = compareBy(
            { !it.isRootPathResolved },
            { it.crate.originOrder() },
            { it.info.usePath },
        )

        private fun Crate.originOrder(): Int = when (origin) {
            PackageOrigin.WORKSPACE -> 0
            PackageOrigin.STDLIB -> when (normName) {
                AutoInjectedCrates.STD -> 1
                AutoInjectedCrates.CORE -> 2
                else -> 3
            }
            PackageOrigin.DEPENDENCY -> 4
            PackageOrigin.STDLIB_DEPENDENCY -> 5
        }
    }
}
