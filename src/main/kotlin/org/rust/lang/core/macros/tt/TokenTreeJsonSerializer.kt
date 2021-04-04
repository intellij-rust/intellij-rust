/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.tt

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import org.rust.lang.core.psi.MacroBraces
import org.rust.stdext.exhaustive
import org.rust.util.RsJacksonSerializer

object TokenTreeJsonSerializer : RsJacksonSerializer<TokenTree>(TokenTree::class.java) {
    override fun serialize(tt: TokenTree, gen: JsonGenerator, provider: SerializerProvider) {
        writeTokenTree(tt, gen)
    }

    private fun writeTokenTree(tt: TokenTree, gen: JsonGenerator) {
        when (tt) {
            is TokenTree.Leaf -> gen.writeJsonObjectWithSingleField("Leaf") { writeLeaf(tt, gen) }
            is TokenTree.Subtree -> gen.writeJsonObjectWithSingleField("Subtree") { writeSubtree(tt, gen) }
        }.exhaustive
    }

    private fun writeLeaf(tt: TokenTree.Leaf, gen: JsonGenerator) {
        when (tt) {
            is TokenTree.Leaf.Literal -> gen.writeJsonObjectWithSingleField("Literal") {
                writeJsonObject {
                    writeStringField("text", tt.text)
                    writeNumberField("id", tt.id.toJson())
                }
            }
            is TokenTree.Leaf.Punct -> gen.writeJsonObjectWithSingleField("Punct") {
                writeJsonObject {
                    writeStringField("char", tt.char)
                    writeStringField("spacing", tt.spacing.toString())
                    writeNumberField("id", tt.id.toJson())
                }
            }
            is TokenTree.Leaf.Ident -> gen.writeJsonObjectWithSingleField("Ident") {
                writeJsonObject {
                    writeStringField("text", tt.text)
                    writeNumberField("id", tt.id.toJson())
                }
            }
        }.exhaustive
    }

    fun writeSubtree(subtree: TokenTree.Subtree, gen: JsonGenerator) {
        gen.writeJsonObject {
            writeNullableField("delimiter", subtree.delimiter) { delimiter ->
                writeJsonObject {
                    writeNumberField("id", delimiter.id.toJson())
                    writeStringField("kind", macroBracesToString(delimiter))
                }
            }
            writeArrayField("token_trees", subtree.tokenTrees) {
                writeTokenTree(it, gen)
            }
        }
    }

    private fun TokenId.toJson(): Long = toLong() and 0xFFFFFFFFL

    private fun macroBracesToString(delimiter: Delimiter): String = when (delimiter.kind) {
        MacroBraces.PARENS -> "Parenthesis"
        MacroBraces.BRACKS -> "Bracket"
        MacroBraces.BRACES -> "Brace"
    }
}

