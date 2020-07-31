/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.runAnything.wasmpack

import org.rust.ide.actions.runAnything.RsRunAnythingItem
import javax.swing.Icon

class RunAnythingWasmPackItem(command: String, icon: Icon) : RsRunAnythingItem(command, icon) {
    override val helpCommand: String = "wasm-pack"

    override val commandDescriptions: Map<String, String> =
        hashMapOf(
            "new" to "Create a new RustWasm project",
            "build" to "Build and pack the project into pkg directory",
            "test" to "Run tests using the wasm-bindgen test runner",
            "pack" to "(npm) Create a tarball from pkg directory",
            "publish" to "(npm) Create a tarball and publish to the NPM registry"
        )

    override fun getOptionsDescriptionsForCommand(commandName: String): Map<String, String>? =
        when (commandName) {
            "build" -> buildOptionsDescriptions
            "test" -> testOptionsDescriptions
            "publish" -> publishOptionsDescriptions
            else -> null
        }

    companion object {
        private val buildOptionsDescriptions: Map<String, String> =
            hashMapOf(
                "--target" to "Output target environment: bundler (default), nodejs, web, no-modules",
                "--dev" to "Development profile: debug info, no optimizations",
                "--profiling" to "Profiling profile: optimizations and debug info",
                "--release" to "Release profile: optimizations, no debug info",
                "--out-dir" to "Output directory",
                "--out-name" to "Generated file names",
                "--scope" to "The npm scope to use"
            )

        private val testOptionsDescriptions: Map<String, String> =
            hashMapOf(
                "--release" to "Build with release profile",
                "--headless" to "Test in headless browser mode",
                "--node" to "Run the tests in Node.js",
                "--firefox" to "Run the tests in Firefox",
                "--chrome" to "Run the tests in Chrome",
                "--safari" to "Run the tests in Safari"
            )

        private val publishOptionsDescriptions: Map<String, String> =
            hashMapOf(
                "--tag" to "NPM tag to publish with"
            )
    }
}

