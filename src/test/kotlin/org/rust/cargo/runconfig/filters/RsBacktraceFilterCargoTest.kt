/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.filters

import org.rust.MaxRustcVersion
import org.rust.MinRustcVersion
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.runconfig.filters.HighlightFilterTestBase.Companion.checkHighlights
import org.rust.fileTree
import org.rust.singleWorkspace

/**
 * Cargo tests for [RsBacktraceFilter]
 */
class RsBacktraceFilterCargoTest : RsWithToolchainTestBase() {
    private val filter: RsBacktraceFilter
        get() = RsBacktraceFilter(project, cargoProjectDirectory, project.cargoProjects.singleWorkspace())

    @MaxRustcVersion("1.69.0")
    fun `test resolve cargo crate git registry`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []

                [dependencies]
                left-pad = "=1.0.1"
            """)

            dir("src") {
                rust("lib.rs", "")
            }
        }.create()

        checkHighlights(
            filter,
            " at /cargo/registry/src/github.com-1ecc6299db9ec823/left-pad-1.0.1/src/lib.rs:21",
            " at [/cargo/registry/src/github.com-1ecc6299db9ec823/left-pad-1.0.1/src/lib.rs:21 -> lib.rs]"
        )
    }

    @MinRustcVersion("1.70.0-nightly")
    fun `test resolve cargo crate sparse registry`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []

                [dependencies]
                left-pad = "=1.0.1"
            """)

            dir("src") {
                rust("lib.rs", "")
            }
        }.create()

        checkHighlights(
            filter,
            " at /cargo/registry/src/index.crates.io-6f17d22bba15001f/left-pad-1.0.1/src/lib.rs:21",
            " at [/cargo/registry/src/index.crates.io-6f17d22bba15001f/left-pad-1.0.1/src/lib.rs:21 -> lib.rs]"
        )
    }
}
