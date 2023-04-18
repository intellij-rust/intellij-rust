/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import java.io.IOException

// Bump each time `RS_BUILTIN_ATTRIBUTES` content is modified
// (that is, `builtin-attributes.json` content is modified)
const val RS_BUILTIN_ATTRIBUTES_VERSION: Int = 1

val RS_BUILTIN_ATTRIBUTES: Map<String, AttributeInfo> = BuiltinAttributeInfoLoader.loadAttributes()

// https://github.com/rust-lang/rust/blob/76d18cfb8945f824c8777e04981e930d2037954e/compiler/rustc_resolve/src/macros.rs#L153
val RS_BUILTIN_TOOL_ATTRIBUTES = setOf("rustfmt", "clippy")

sealed interface AttributeInfo

data class BuiltinAttributeInfo(
    val name: String,
    val type: AttributeType,
    val template: AttributeTemplate,
    val duplicates: AttributeDuplicates,
    val gated: Boolean,
) : AttributeInfo

object BuiltinProcMacroInfo : AttributeInfo

@Suppress("unused")
enum class AttributeType {
    Normal, CrateLevel
}

data class AttributeTemplate(
    val word: Boolean,
    val list: String?,
    val nameValueStr: String?,
)

@Suppress("unused")
enum class AttributeDuplicates {
    DuplicatesOk,
    WarnFollowing,
    WarnFollowingWordOnly,
    ErrorFollowing,
    ErrorPreceding,
    FutureWarnFollowing,
    FutureWarnPreceding
}

private object BuiltinAttributeInfoLoader {
    private const val BUILTIN_ATTRIBUTES_PATH = "compiler-info/builtin-attributes.json"

    private val LOG: Logger = logger<BuiltinAttributeInfoLoader>()

    fun loadAttributes(): Map<String, AttributeInfo> {
        val attributeList: List<BuiltinAttributeInfo> = try {
            val stream = BuiltinAttributeInfo::class.java.classLoader
                .getResourceAsStream(BUILTIN_ATTRIBUTES_PATH)
            if (stream == null) {
                LOG.error("Can't find `$BUILTIN_ATTRIBUTES_PATH` file in resources")
                return emptyMap()
            }
            stream.buffered().use {
                jacksonObjectMapper().readValue(it)
            }
        } catch (e: IOException) {
            LOG.error(e)
            emptyList()
        }

        val associateMap: HashMap<String, AttributeInfo> =
            attributeList.associateByTo(hashMapOf(), BuiltinAttributeInfo::name)

        // Internal stdlib proc macros

        associateMap["simd_test"] = BuiltinProcMacroInfo
        associateMap["assert_instr"] = BuiltinProcMacroInfo

        // Proc macros from stdlib. Defined like
        // `pub macro test($item:item) {}`
        // TODO resolve them correctly and remove from this list?

        associateMap["derive"] = BuiltinProcMacroInfo
        associateMap["test"] = BuiltinProcMacroInfo
        associateMap["bench"] = BuiltinProcMacroInfo
        associateMap["test_case"] = BuiltinProcMacroInfo
        associateMap["global_allocator"] = BuiltinProcMacroInfo
        associateMap["cfg_accessible"] = BuiltinProcMacroInfo

        // Don't forget to bump `RS_BUILTIN_ATTRIBUTES_VERSION` each time `associateMap` is modified!

        return associateMap
    }
}
