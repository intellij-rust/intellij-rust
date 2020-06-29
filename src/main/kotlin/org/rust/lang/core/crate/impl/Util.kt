/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.crate.impl

import com.intellij.openapiext.Testmark
import org.rust.lang.core.crate.Crate

fun Iterable<Crate.Dependency>.flattenTopSortedDeps(): LinkedHashSet<Crate> {
    val flatDeps = linkedSetOf<Crate>()

    for (dep in this) {
        for (flatDep in dep.crate.flatDependencies) {
            flatDeps += flatDep
        }
        flatDeps += dep.crate
    }

    return flatDeps
}

object CrateGraphTestmarks {
    val cyclicDevDependency = Testmark("cyclicDevDependency")
}
