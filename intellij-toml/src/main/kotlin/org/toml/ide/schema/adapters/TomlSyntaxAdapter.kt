/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema.adapters

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter
import com.jetbrains.jsonSchema.impl.JsonSchemaType
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlPsiFactory

class TomlSyntaxAdapter(project: Project) : JsonLikeSyntaxAdapter {

    private val factory = TomlPsiFactory(project)

    override fun getPropertyValue(property: PsiElement): PsiElement? {
        return (property as? TomlKeyValue)?.value
    }

    override fun getPropertyName(property: PsiElement): String? {
        return (property as? TomlKeyValue)?.key?.text
    }

    override fun createProperty(name: String, value: String, element: PsiElement?): PsiElement {
        return factory.createKeyValue(name, value)
    }

    override fun fixWhitespaceBefore(initialElement: PsiElement?, element: PsiElement?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }



    override fun adjustPropertyAnchor(element: LeafPsiElement?): PsiElement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }



    override fun ensureComma(self: PsiElement?, newElement: PsiElement?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun adjustNewProperty(element: PsiElement?): PsiElement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun removeIfComma(forward: PsiElement?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getDefaultValueFromType(type: JsonSchemaType?): String {
        // TODO?
        return type?.defaultValue.orEmpty()
    }
}
