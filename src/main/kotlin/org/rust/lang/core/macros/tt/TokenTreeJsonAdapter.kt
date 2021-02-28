/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.tt

import com.google.gson.*
import java.lang.reflect.Type

class TokenTreeJsonAdapter : JsonSerializer<TokenTree>, JsonDeserializer<TokenTree> {
    override fun serialize(json: TokenTree, type: Type, context: JsonSerializationContext): JsonElement {
        return when (json) {
            is TokenTree.Leaf -> JsonObject().apply {
                val serializedLeaf = when (json) {
                    is TokenTree.Leaf.Literal -> JsonObject().apply {
                        add("Literal", context.serialize(json, json.javaClass))
                    }
                    is TokenTree.Leaf.Punct -> JsonObject().apply {
                        add("Punct", context.serialize(json, json.javaClass))
                    }
                    is TokenTree.Leaf.Ident -> JsonObject().apply {
                        add("Ident", context.serialize(json, json.javaClass))
                    }
                }
                add("Leaf", serializedLeaf)
            }
            is TokenTree.Subtree -> JsonObject().apply {
                add("Subtree", context.serialize(json, json.javaClass))
            }
        }
    }

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, type: Type, context: JsonDeserializationContext): TokenTree? {
        val obj = json.asJsonObject
        return when {
            obj.has("Leaf") -> {
                val leaf = obj["Leaf"].asJsonObject
                return when {
                    leaf.has("Literal") -> context.deserialize(leaf["Literal"], TokenTree.Leaf.Literal::class.java)
                    leaf.has("Punct") -> context.deserialize(leaf["Punct"], TokenTree.Leaf.Punct::class.java)
                    leaf.has("Ident") -> context.deserialize(leaf["Ident"], TokenTree.Leaf.Ident::class.java)
                    else -> null
                }
            }
            obj.has("Subtree") -> context.deserialize(obj["Subtree"], TokenTree.Subtree::class.java)
            else -> null
        }
    }
}
