/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * https://doc.rust-lang.org/cargo/reference/config.html
 */
data class CargoConfig(
    val buildTargets: List<String>,
    val env: Map<String, EnvValue>,

) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class EnvValue(
        @JsonProperty("value")
        val value: String,
        @JsonProperty("force")
        val isForced: Boolean = false,
        @JsonProperty("relative")
        val isRelative: Boolean = false
    )

    companion object {
        val DEFAULT = CargoConfig(emptyList(), emptyMap())
    }
}
