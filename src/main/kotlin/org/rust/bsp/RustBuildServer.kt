/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp

import org.rust.cargo.toolchain.impl.CargoMetadata
import java.util.concurrent.CompletableFuture

// TODO: Move this to bsp4j
class RustBuildServer {

    fun projectPackages(): CompletableFuture<List<CargoMetadata.Package>> {
        return CompletableFuture.completedFuture(listOf())
    }

    fun projectDependencies(): CompletableFuture<List<CargoMetadata.ResolveNode>> {
        return CompletableFuture.completedFuture(listOf())
    }

    fun version(): CompletableFuture<Int> {
        return CompletableFuture.completedFuture(1)
    }

    fun workspaceMembers(): CompletableFuture<List<String>> {
        return CompletableFuture.completedFuture(listOf())
    }

    fun workspaceRoot(): CompletableFuture<String> {
        return CompletableFuture.completedFuture("")
    }
}
