/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext
import com.vdurmont.semver4j.Semver
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.descendantOfType
import org.rust.openapiext.isFeatureEnabled
import org.rust.toml.*
import org.rust.toml.crates.local.CrateVersionRequirement
import org.rust.toml.crates.local.CratesLocalIndexException
import org.rust.toml.crates.local.CratesLocalIndexService
import org.rust.toml.resolve.allFeatures
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlKeyValue

/**
 * Consider `Cargo.toml`:
 * ```
 * [dependencies]
 * foo = { version = "*", features = ["<caret>"] }
 *                                    #^ Provides completion here
 *
 * [dependencies.foo]
 * features = ["<caret>"]
 *             #^ Provides completion here
 * ```
 *
 * It uses info from both downloaded packages and crates local index.
 *
 * @see [org.rust.toml.resolve.CargoTomlDependencyFeaturesReferenceProvider]
 */
class CargoTomlDependencyFeaturesCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val containingArray = parameters.position.ancestorOrSelf<TomlArray>() ?: return
        val pkgName = containingArray.containingDependencyKey?.text ?: return
        val pkgVersion = containingArray.parent?.parent?.descendantOfType<TomlKeyValue> {
            it.key.segments.singleOrNull()?.text == "version"
        }?.value?.stringValue ?: return
        val versionReq = CrateVersionRequirement.build(pkgVersion) ?: return

        if (!getFeaturesFromFiles(containingArray, pkgName, versionReq, result) && isFeatureEnabled(RsExperiments.CRATES_LOCAL_INDEX)) {
            getFeaturesFromLocalIndex(pkgName, versionReq, result)
        }
    }

    private fun getFeaturesFromFiles(containingArray: TomlArray, pkgName: String, versionReq: CrateVersionRequirement, result: CompletionResultSet): Boolean {
        val depToml = findDependencyTomlFile(containingArray, pkgName)
        val depTomlVersion = containingArray.findCargoPackageForCargoToml()?.findDependencyByPackageName(pkgName)?.version ?: return false
        val depTomlSemver = Semver(depTomlVersion, Semver.SemverType.NPM)

        if (depToml != null && versionReq.matches(depTomlSemver)) {
            // TODO avoid AST loading?
            for (feature in depToml.allFeatures()) {
                result.addElement(lookupElementForFeature(feature))
            }
            return true
        }
        return false
    }

    private fun getFeaturesFromLocalIndex(pkgName: String, versionReq: CrateVersionRequirement, result: CompletionResultSet) {
        val crate = try {
            CratesLocalIndexService.getInstance().getCrate(pkgName) ?: return
        } catch (e: CratesLocalIndexException) {
            return
        }

        val compatibleVersion = crate.sortedVersions.filter {
            val version = it.semanticVersion ?: return@filter false
            versionReq.matches(version)
        }.lastOrNull() ?: return

        result.addAllElements(compatibleVersion.features.map {
            LookupElementBuilder
                .create(it)
                .withInsertHandler(StringLiteralInsertionHandler())
        })
    }
}
