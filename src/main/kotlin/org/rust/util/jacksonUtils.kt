/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

// TODO: drop this file when https://youtrack.jetbrains.com/issue/KTIJ-19058 will be resolved
package org.rust.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

fun kotlinModule(initializer: KotlinModule.Builder.() -> Unit = {}): KotlinModule {
    val builder = KotlinModule.Builder()
    builder.initializer()
    return builder.build()
}

inline fun <reified T> jacksonTypeRef(): TypeReference<T> = object: TypeReference<T>() {}

fun ObjectMapper.registerKotlinModule(): ObjectMapper = this.registerModule(kotlinModule())

inline fun <reified T> ObjectMapper.readValue(content: String): T = readValue(content, jacksonTypeRef<T>())
