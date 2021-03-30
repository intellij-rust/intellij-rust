/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.tt

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import org.rust.lang.core.psi.MacroBraces
import org.rust.util.RsJacksonDeserializer

object TokenTreeJsonDeserializer : RsJacksonDeserializer<TokenTree>(TokenTree::class.java) {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): TokenTree {
        return readTokenTree(context)
    }

    private fun readTokenTree(context: DeserializationContext): TokenTree {
        return context.readSingleFieldObject { key ->
            when (key) {
                "Leaf" -> readLeaf(context)
                "Subtree" -> readSubtree(context)
                else -> context.reportInputMismatch("Unknown token tree kind: $key")
            }
        }
    }

    private fun readLeaf(context: DeserializationContext): TokenTree.Leaf {
        return context.readSingleFieldObject { key ->
            when (key) {
                "Literal" -> readTextAndId("text", TokenTree.Leaf::Literal)
                "Punct" -> readPunct()
                "Ident" -> readTextAndId("text", TokenTree.Leaf::Ident)
                else -> reportInputMismatch("Unknown leaf kind: $key")
            }
        }
    }

    private fun DeserializationContext.readPunct(): TokenTree.Leaf.Punct {
        var char: String? = null
        var spacingString: String? = null
        var id: TokenId = -1
        readObjectFields { key1 ->
            when (key1) {
                "char" -> char = readString()
                "spacing" -> spacingString = readString()
                "id" -> id = readTokenId()
            }
        }
        val char1 = char ?: reportInputMismatch("Missing `char` field")
        val spacing = spacingFromString(spacingString)
            ?: reportInputMismatch("Unknown spacing: $spacingString")
        return TokenTree.Leaf.Punct(char1, spacing, id)
    }

    fun readSubtree(context: DeserializationContext): TokenTree.Subtree {
        var delimiterParsed: Delimiter? = null
        val tokenTrees = mutableListOf<TokenTree>()

        context.readObjectFields { key ->
            when (key) {
                "delimiter" -> {
                    delimiterParsed = readTextAndIdNullable("kind") { kind, id ->
                        val braces = macroBracesFromString(kind) ?: reportInputMismatch("Unknown delimiter kind: $kind")
                        Delimiter(id, braces)
                    }
                }
                "token_trees" -> readArray(tokenTrees) {
                    readTokenTree(context)
                }
            }
        }

        return TokenTree.Subtree(delimiterParsed, tokenTrees)
    }

    private inline fun <T : Any> DeserializationContext.readTextAndIdNullable(
        textFieldName: String,
        constructor: (String, Int) -> T
    ): T? {
        if (parser.currentToken == JsonToken.VALUE_NULL) return null
        return readTextAndId(textFieldName, constructor)
    }

    private inline fun <T> DeserializationContext.readTextAndId(textFieldName: String, constructor: (String, Int) -> T): T {
        var text: String? = null
        var id: TokenId = -1
        readObjectFields { key1 ->
            when (key1) {
                textFieldName -> text = readString()
                "id" -> id = readTokenId()
            }
        }
        val text1 = text ?: reportInputMismatch("Missing `$textFieldName` field")
        return constructor(text1, id)
    }

    private fun DeserializationContext.readTokenId(): TokenId = readLong().toInt()

    private fun macroBracesFromString(kind: String): MacroBraces? = when (kind) {
        "Parenthesis" -> MacroBraces.PARENS
        "Bracket" -> MacroBraces.BRACKS
        "Brace" -> MacroBraces.BRACES
        else -> null
    }

    private fun spacingFromString(spacingString: String?) = when (spacingString) {
        "Alone" -> Spacing.Alone
        "Joint" -> Spacing.Joint
        else -> null
    }
}
