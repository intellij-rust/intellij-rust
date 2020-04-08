/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.workspace

import org.rust.lang.utils.PresentableNodeData

/** A [cargo feature](https://doc.rust-lang.org/cargo/reference/features.html) with [name] in some package [pkg] */
data class PackageFeature(val pkg: CargoWorkspace.Package, val name: FeatureName) : PresentableNodeData {
    override val text: String
        get() = "${pkg.name}/$name"

    override fun toString(): String = text
}

enum class FeatureState {
    Enabled,
    Disabled;

    val isEnabled: Boolean
        get() = when (this) {
            Enabled -> true
            Disabled -> false
        }

    operator fun not(): FeatureState = when (this) {
        Enabled -> Disabled
        Disabled -> Enabled
    }
}
