/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.regions

import org.rust.lang.core.types.Kind
import org.rust.lang.core.types.TypeFlags
import org.rust.lang.core.types.infer.TypeFoldable
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor

/**
 * We use the terms `region` and `lifetime` interchangeably.
 * The name `Region` inspired by the Rust compiler.
 */
abstract class Region : Kind, TypeFoldable<Region> {
    override val flags: TypeFlags = 0

    override fun superFoldWith(folder: TypeFolder): Region = folder.foldRegion(this)
    override fun superVisitWith(visitor: TypeVisitor): Boolean = visitor.visitRegion(this)
}
