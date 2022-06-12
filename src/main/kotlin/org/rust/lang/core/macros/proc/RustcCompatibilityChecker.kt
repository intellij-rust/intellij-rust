/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.io.delete
import com.intellij.util.io.write
import org.intellij.lang.annotations.Language
import org.rust.cargo.project.model.RustcInfo
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.cargo.toolchain.impl.RustcVersion
import org.rust.cargo.toolchain.tools.cargo
import org.rust.cargo.util.parseSemVer
import org.rust.lang.core.macros.errors.ProcMacroExpansionError
import org.rust.lang.core.macros.tt.TokenTree
import org.rust.lang.core.macros.tt.parseSubtree
import org.rust.lang.core.parser.createRustPsiBuilder
import org.rust.openapiext.RsPathManager
import org.rust.openapiext.StdOutputCollectingProcessListener
import org.rust.stdext.RsResult
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok
import org.rust.stdext.andThen
import org.rust.stdext.randomLowercaseAlphabetic
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Checks that a given Rust Compiler is supported by our proc macro expander
 */
@Service
class RustcCompatibilityChecker : Disposable {
    // Guarded by self object monitor (@Synchronized)
    private val cache: MutableMap<CachingKey, CompletableFuture<RsResult<Unit, IncompatibilityCause>>> = hashMapOf()

    // Guarded by self object monitor (@Synchronized)
    private val progressIndicators: MutableList<ProgressIndicator> = mutableListOf()

    @Synchronized // The method should be fast enough
    fun isRustcCompatibleWithProcMacroExpander(
        project: Project,
        toolchain: RsToolchainBase,
        rustcInfo: RustcInfo,
        rustcVersion: RustcVersion,
    ): CompletableFuture<RsResult<Unit, IncompatibilityCause>> {
        if (!ProcMacroApplicationService.isEnabled()) {
            return CompletableFuture.completedFuture(Ok(Unit))
        }
        if (rustcVersion.semver < MIN_SUPPORTED_TOOLCHAIN) {
            return CompletableFuture.completedFuture(Err(IncompatibilityCause.TooOldRustCompiler))
        }

        return cache.getOrPut(CachingKey(toolchain.location, rustcInfo.rustupActiveToolchain, rustcVersion)) {
            val future = CompletableFuture<RsResult<Unit, IncompatibilityCause>>()
            val task = object : Task.Backgroundable(project, "Checking Rustc compatibility with proc macro expander", false) {
                override fun run(indicator: ProgressIndicator) {
                    val result = try {
                        checkRustcCompatibleWithProcMacroExpander(project, toolchain, rustcInfo, rustcVersion)
                    } catch (t: Throwable) {
                        future.completeExceptionally(t)
                        throw t
                    } finally {
                        onTaskFinish(indicator)
                    }
                    future.complete(result)
                }
            }
            val progressIndicator = EmptyProgressIndicator(ModalityState.any())
            progressIndicators += progressIndicator
            ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, progressIndicator)
            future
        }
    }

    @Synchronized
    private fun onTaskFinish(indicator: ProgressIndicator) {
        progressIndicators.remove(indicator)
    }

    @Synchronized
    override fun dispose() {
        for (indicator in progressIndicators) {
            indicator.cancel()
        }
        for (future in cache.values) {
            future.cancel(true)
        }
        cache.clear()
    }

    private fun checkRustcCompatibleWithProcMacroExpander(
        project: Project,
        toolchain: RsToolchainBase,
        rustcInfo: RustcInfo,
        version: RustcVersion,
    ): RsResult<Unit, IncompatibilityCause> {
        val start = System.currentTimeMillis()

        val root = RsPathManager.tempPluginDirInSystem()
            .resolve("proc-macros-compat-check")
            .resolve(randomLowercaseAlphabetic(16))

        try {
            try {
                makeTempProcMacroProject(root, rustcInfo)
            } catch (e: IOException) {
                LOG.error("Failed to create a temp proc macro project at $root", e)
                return Ok(Unit)
            }

            val outputCollector = StdOutputCollectingProcessListener()

            val buildMessages = toolchain.cargo()
                .fetchBuildScriptsInfo(project, root, outputCollector)
            val procMacroArtifactPath = CargoMetadata.findProcMacroArtifact(buildMessages.messages.values.flatten())

            if (procMacroArtifactPath == null) {
                LOG.warn(
                    "Failed to compile the reference proc macro project.\n" +
                        "Toolchain: ${toolchain.location},\n" +
                        "Rustup toolchain: ${rustcInfo.rustupActiveToolchain},\n" +
                        "Rustc version: $version,\n" +
                        "Process stdout: ${outputCollector.stdout}" +
                        "Process stderr: ${outputCollector.stderr}"
                )
                return Err(IncompatibilityCause.ProcMacroCrateCompilationError)
            }

            val expander = ProcMacroExpander(project, toolchain, timeout = 20_000)

            fun expand(
                macroName: String,
                macroCallBody: TokenTree.Subtree,
                env: Map<String, String> = emptyMap()
            ): RsResult<Unit, IncompatibilityCause> {
                return expander.expandMacroAsTtWithErr(
                    macroCallBody,
                    null,
                    macroName,
                    procMacroArtifactPath,
                    env
                ).map {}.mapErr { IncompatibilityCause.ExpansionError(macroName, it) }
            }

            val input = project.createRustPsiBuilder("input (.) :: 123").parseSubtree().subtree

            val result = expand("test_macro", input)
                .andThen { expand("function_like_read_env_var", input, mapOf("FOO_ENV_VAR" to "val")) }

            val elapsed = System.currentTimeMillis() - start
            LOG.info("Finished proc macro compatibility check in $elapsed ms. Result: $result")
            return result
        } finally {
            try {
                root.delete()
            } catch (ignored: IOException) {
            }
        }
    }

    @Throws(IOException::class)
    private fun makeTempProcMacroProject(root: Path, rustcInfo: RustcInfo) {
        val src = root.resolve("src")
        Files.createDirectories(src)

        val rustupActiveToolchain = rustcInfo.rustupActiveToolchain
        if (rustupActiveToolchain != null) {
            root.resolve("rust-toolchain.toml").write(
                """
                [toolchain]
                channel = "$rustupActiveToolchain"
            """.trimIndent()
            )
        }

        @Language("TOML")
        val cargoToml = """
            [package]
            name = "test-proc-macro"
            version = "0.1.0"
            edition = "2018"

            [lib]
            proc-macro = true
            path = "src/lib.rs"

            # A binary target is needed in order to make Cargo compile our proc macro as a shared library
            [[bin]]
            path = "src/main.rs"
            name = "placeholder"

            [dependencies]
        """.trimIndent()
        root.resolve("Cargo.toml").write(cargoToml)

        @Language("Rust")
        val procMacroSrc = """
            extern crate proc_macro;

            use proc_macro::TokenStream;

            #[proc_macro]
            pub fn test_macro(input: TokenStream) -> TokenStream {
                // Try doing something non-trivial and use more proc_macro API
                let tts = input.into_iter().collect::<Vec<_>>();
                tts.iter().enumerate().map(|(i, tt)| {
                    let mut tt2 = tt.clone();
                    tt2.set_span(tts[tts.len() - 1 - i].span());
                    tt2
                }).collect::<TokenStream>()
            }

            #[proc_macro]
            pub fn function_like_read_env_var(input: TokenStream) -> TokenStream {
                use std::fmt::Write;
                let v = std::env::var("FOO_ENV_VAR").unwrap();
                let mut s = String::new();
                write!(&mut s, "\"{}\"", v);
                return s.parse().unwrap();
            }
        """.trimIndent()
        src.resolve("lib.rs").write(procMacroSrc)
        src.resolve("main.rs").write("fn main() {}")
    }

    private data class CachingKey(
        val toolchainLocation: Path,
        val rustupActiveToolchain: String?,
        val rustcVersion: RustcVersion
    )

    sealed class IncompatibilityCause {
        object TooOldRustCompiler : IncompatibilityCause()
        object ProcMacroCrateCompilationError : IncompatibilityCause()
        data class ExpansionError(
            val macroName: String,
            val error: ProcMacroExpansionError
        ) : IncompatibilityCause()
    }

    companion object {
        private val LOG = logger<RustcCompatibilityChecker>()

        // TODO use `RsToolchainBase.MIN_SUPPORTED_TOOLCHAIN` after its bumping
        private val MIN_SUPPORTED_TOOLCHAIN = "1.47.0".parseSemVer()

        @JvmStatic
        fun getInstance(): RustcCompatibilityChecker = service()
    }
}
