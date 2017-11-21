/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.VirtualFilePattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.intellij.lang.annotations.Language
import org.rust.lang.core.psi.ext.ancestorStrict
import org.toml.lang.psi.*


class CargoTomlKeysCompletionProvider : CompletionProvider<CompletionParameters>() {
    private var cachedSchema: TomlSchema? = null

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext?,
        result: CompletionResultSet
    ) {
        val schema = cachedSchema ?:
            TomlSchema.parse(parameters.position.project, EXAMPLE_CARGO_TOML).also { cachedSchema = it }

        val key = parameters.position.parent as? TomlKey ?: return
        val table = key.topLevelTable ?: return
        val parent = key.parent
        val variants = when (parent) {
            is TomlTableHeader -> {
                if (key != parent.names.firstOrNull()) return
                val isArray = when (table) {
                    is TomlArrayTable -> true
                    is TomlTable -> false
                    else -> return
                }
                schema.topLevelKeys(isArray)
            }

            is TomlKeyValue -> {
                val tableName = (table as? TomlHeaderOwner)?.header?.names?.firstOrNull()?.text
                    ?: return
                schema.keysForTable(tableName)
            }

            else -> return
        }

        result.addAllElements(variants.map {
            LookupElementBuilder.create(it)
        })
    }

    private val TomlKey.topLevelTable: TomlKeyValueOwner?
        get() {
            val table = ancestorStrict<TomlKeyValueOwner>() ?: return null
            if (table.parent !is TomlFile) return null
            return table
        }

    companion object {
        val elementPattern: ElementPattern<PsiElement>
            get() = PlatformPatterns.psiElement()
                .inVirtualFile(VirtualFilePattern().withName("Cargo.toml"))
                .withParent(TomlKey::class.java)
    }
}



// Example from http://doc.crates.io/manifest.html,
// basic completion is automatically generated from it.
@Language("TOML")
private val EXAMPLE_CARGO_TOML = """

[package]
name = "hello_world" # the name of the package
version = "0.1.0"    # the current version, obeying semver
authors = ["you@example.com"]
build = "build.rs"
documentation = "https://docs.rs/example"
exclude = ["build/**/*.o", "doc/**/*.html"]
include = ["src/**/*", "Cargo.toml"]
publish = false
workspace = "path/to/workspace/root"

description = "..."
homepage = "..."
repository = "..."
readme = "..."
keywords = ["...", "..."]
categories = ["...", "..."]
license = "..."
license-file = "..."

[badges]
appveyor = { repository = "...", branch = "master", service = "github" }
circle-ci = { repository = "...", branch = "master" }
gitlab = { repository = "...", branch = "master" }
travis-ci = { repository = "...", branch = "master" }
codecov = { repository = "...", branch = "master", service = "github" }
coveralls = { repository = "...", branch = "master", service = "github" }
is-it-maintained-issue-resolution = { repository = "..." }
is-it-maintained-open-issues = { repository = "..." }
maintenance = { status = "..." }

[profile.release]
opt-level = 3
debug = false
rpath = false
lto = false
debug-assertions = false
codegen-units = 1
panic = 'unwind'

[features]
default = ["jquery", "uglifier", "session"]

[workspace]
members = ["path/to/member1", "path/to/member2", "path/to/member3/*"]
exclude = ["path1", "path/to/dir2"]

[dependencies]
foo = { git = 'https://github.com/example/foo' }

[lib]
name = "foo"
path = "src/lib.rs"
test = true
doctest = true
bench = true
doc = true
plugin = false
proc-macro = false
harness = true

[[example]]
name = "foo"
path = "src/lib.rs"
test = true
doctest = true
bench = true
doc = true
plugin = false
proc-macro = false
harness = true
required-features = ["postgres", "tools"]

[[bin]]
name = "foo"
path = "src/lib.rs"
test = true
doctest = true
bench = true
doc = true
plugin = false
proc-macro = false
harness = true
required-features = ["postgres", "tools"]

[[test]]
name = "foo"
path = "src/lib.rs"
test = true
doctest = true
bench = true
doc = true
plugin = false
proc-macro = false
harness = true
required-features = ["postgres", "tools"]

[[bench]]
name = "foo"
path = "src/lib.rs"
test = true
doctest = true
bench = true
doc = true
plugin = false
proc-macro = false
harness = true
required-features = ["postgres", "tools"]

[patch.crates-io]
foo = { git = 'https://github.com/example/foo' }
"""
