/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core

import com.intellij.util.text.SemVer

data class CompilerFeature(val name: String, val state: FeatureState, val since: SemVer) {
    constructor(name: String, state: FeatureState, since: String) : this(name, state, SemVer.parseFromText(since)!!)
}

enum class FeatureState {
    /**
     * Represents active features that are currently being implemented or
     * currently being considered for addition/removal.
     * Such features can be used only with nightly compiler with the corresponding feature attribute
     */
    ACTIVE,
    /**
     * Those language feature has since been Accepted (it was once Active)
     * so such language features can be used with stable/beta compiler since some version
     * without any additional attributes
     */
    ACCEPTED
}
