/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.lang.core.crate.Crate

/**
 * Used for optimization purposes, to reduce access to cache and PSI tree in some very hot places,
 * [ImplLookup.processTyFingerprintsWithAliases] in particular
 */
class RsCachedTypeAlias(
    val name: String,
) {
    val containingCrates: MutableSet<Crate> = hashSetOf()
}
