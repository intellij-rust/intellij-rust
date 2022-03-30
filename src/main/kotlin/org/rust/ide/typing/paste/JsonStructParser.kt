/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing.paste

import com.fasterxml.jackson.core.JsonFactoryBuilder
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.intellij.grazie.utils.toLinkedSet
import java.io.IOException

sealed class DataType {
    object Integer : DataType()
    object Float : DataType()
    object Boolean : DataType()
    object String : DataType()
    object Unknown : DataType()
    data class StructRef(val struct: Struct) : DataType()
    data class Array(val type: DataType) : DataType()
    data class Nullable(val type: DataType) : DataType()
}

// Structs with the same field names and types are considered the same, regardless of field order.
// LinkedHashMap::equals ignores key (field) order.
data class Struct(val fields: LinkedHashMap<String, DataType>)

/**
 * Try to parse the input text as a JSON object.
 *
 * Extract a list of (unique) structs that were encountered in the JSON object.
 */
fun extractStructsFromJson(text: String): List<Struct>? {
    // Fast path to avoid creating a parser if the input does not look like a JSON object
    val input = text.trim()
    if (input.isEmpty() || input.first() != '{' || input.last() != '}') return null

    return try {
        val factory = JsonFactoryBuilder()
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build()
        val parser = factory.createParser(input)

        val structParser = StructParser()
        val rootStruct = parser.use {
            // There must be an object at the root
            val token = parser.nextToken()
            if (token != JsonToken.START_OBJECT) return null

            structParser.parseStruct(parser)
        } ?: return null

        // Return structs in reversed order to generate the inner structs first
        gatherEncounteredStructs(rootStruct).toList().reversed()
    } catch (e: IOException) {
        null
    }
}

data class Field(val name: String, val type: DataType)

/**
 * Finds all unique structs contained in the root struct.
 */
fun gatherEncounteredStructs(root: Struct): Set<Struct> {
    val structs = linkedSetOf<Struct>()
    gatherStructs(DataType.StructRef(root), structs)
    return structs
}

private fun gatherStructs(type: DataType, structs: MutableSet<Struct>) {
    when (type) {
        is DataType.Array -> gatherStructs(type.type, structs)
        is DataType.Nullable -> gatherStructs(type.type, structs)
        is DataType.StructRef -> {
            structs.add(type.struct)
            for (fieldType in type.struct.fields.values) {
                gatherStructs(fieldType, structs)
            }
        }
        DataType.Boolean, DataType.Float, DataType.Integer,
        DataType.String, DataType.Unknown -> {}
    }
}

private class StructParser {
    private val structMap = linkedSetOf<Struct>()

    fun parseStruct(parser: JsonParser): Struct? {
        if (parser.currentToken != JsonToken.START_OBJECT) return null

        val fields = linkedMapOf<String, DataType>()
        while (true) {
            val field = parseField(parser) ?: break

            // Ignore duplicate fields
            if (field.name in fields) continue
            fields[field.name] = field.type
        }

        if (parser.currentToken != JsonToken.END_OBJECT) return null

        val struct = Struct(fields)
        registerStruct(struct)
        return struct
    }

    private fun registerStruct(struct: Struct) {
        structMap.add(struct)
    }

    private fun parseField(parser: JsonParser): Field? {
        if (parser.nextToken() != JsonToken.FIELD_NAME) return null

        val name = parser.currentName
        val type = parseDataType(parser)
        return Field(name, type)
    }

    private fun parseArray(parser: JsonParser): DataType? {
        if (parser.currentToken != JsonToken.START_ARRAY) return null

        val foundDataTypes = linkedSetOf<DataType>()
        val firstType = parseDataType(parser)

        // Empty array
        if (firstType == DataType.Unknown) {
            return DataType.Array(DataType.Unknown)
        }

        foundDataTypes.add(firstType)
        while (parser.currentToken != null) {
            val dataType = parseDataType(parser)
            if (dataType == DataType.Unknown && parser.currentToken == JsonToken.END_ARRAY) {
                break
            }
            foundDataTypes.add(dataType)
        }

        return DataType.Array(generateContainedType(foundDataTypes))
    }

    private fun extractStructType(type: DataType): DataType.StructRef? {
        return when {
            type is DataType.StructRef -> type
            type is DataType.Nullable && type.type is DataType.StructRef -> type.type
            else -> null
        }
    }

    private fun generateContainedType(types: Set<DataType>): DataType {
        val containsNull = types.any { it is DataType.Nullable }
        val structTypes = types.mapNotNull { extractStructType(it) }
        val innerType = when {
            types.size == 1 -> types.first()
            types.size == 2 && containsNull -> DataType.Nullable(types.first { it !is DataType.Nullable })
            structTypes.size == types.size -> {
                val type = unifyStructs(structTypes)
                if (containsNull) {
                    DataType.Nullable(type)
                } else {
                    type
                }
            }
            containsNull -> DataType.Nullable(DataType.Unknown)
            else -> DataType.Unknown
        }
        return innerType
    }

    /**
     * Look through a list of structs and try to unify them to a single struct type with optional fields.
     */
    private fun unifyStructs(structTypes: List<DataType.StructRef>): DataType {
        // Gather all field keys
        val foundFields = structTypes
            .flatMap { it.struct.fields.entries }
            .groupByTo(mutableMapOf(), { it.key }, { it.value })

        // Add null to fields that are not inside all structs
        for (types in foundFields.values) {
            if (types.size < structTypes.size) {
                types.add(DataType.Nullable(types[0]))
            }
        }

        // Use toLinkedSet to keep a deterministic order
        val fields = foundFields.mapValuesTo(linkedMapOf()) { generateContainedType(it.value.toLinkedSet()) }

        val struct = Struct(fields)
        registerStruct(struct)
        return DataType.StructRef(struct)
    }

    private fun parseDataType(parser: JsonParser): DataType {
        return when (parser.nextToken()) {
            JsonToken.START_OBJECT -> {
                val struct = parseStruct(parser)
                if (struct == null) {
                    DataType.Unknown
                } else {
                    DataType.StructRef(struct)
                }
            }
            JsonToken.START_ARRAY -> parseArray(parser) ?: DataType.Unknown
            JsonToken.VALUE_NULL -> DataType.Nullable(DataType.Unknown)
            JsonToken.VALUE_FALSE, JsonToken.VALUE_TRUE -> DataType.Boolean
            JsonToken.VALUE_NUMBER_INT -> DataType.Integer
            JsonToken.VALUE_NUMBER_FLOAT -> DataType.Float
            JsonToken.VALUE_STRING -> DataType.String
            else -> DataType.Unknown
        }
    }
}
