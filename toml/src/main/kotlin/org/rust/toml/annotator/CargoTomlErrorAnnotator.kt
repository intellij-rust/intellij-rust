/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.annotator

import com.intellij.ide.annotator.AnnotatorBase
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiElement
import org.rust.toml.CargoTomlDependencyVisitor
import org.rust.toml.crates.getCrate
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlValue

class CargoTomlErrorAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        object : CargoTomlDependencyVisitor() {
            override fun visitDependency(name: TomlKey, version: TomlValue?) {
                checkDependency(name, version, holder)
            }
        }.visitElement(element)
    }

    private fun checkDependency(dependency: TomlKey, version: TomlValue?, holder: AnnotationHolder) {
        val crate = getCrate(dependency)

        if (crate == null) {
            holder.annotateError(dependency, "Crate doesn't exist")
            return
        }

        // Version must be present past this point (it could've been not present because the user didn't type it yet)
        if (version == null) {
            return
        }

        // TODO: use version req
        val versionValue = version.text.removeSurrounding("\"")
        val versionInfo = crate.versions.find { it.version.rawVersion == versionValue }

        if (crate.versions.all { it.yanked }) {
            holder.annotateError(
                dependency.parent,
                "Crate has no available (non-yanked) versions"
            )
        } else {
            if (versionInfo == null) {
                holder.annotateError(
                    version,
                    "Version doesn't exist"
                )
            } else if (versionInfo.yanked) {
                holder.annotateError(
                    version,
                    "Version has been yanked and is no longer available"
                )
            }
        }
    }

    private fun AnnotationHolder.annotateError(element: PsiElement, message: String) {
        // BACKCOMPAT: 2019.3
        @Suppress("DEPRECATION")
        createErrorAnnotation(
            element,
            message
        )
    }
}
