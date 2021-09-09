/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.schema

import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType

class RsCargoManifestSchemaProvider : JsonSchemaFileProvider {
    override fun isAvailable(file: VirtualFile): Boolean =
        file.name == "Cargo.toml"

    override fun getName(): String = "Cargo Manifest"

    override fun getSchemaFile(): VirtualFile? =
        JsonSchemaProviderFactory.getResourceFile(this::class.java, "/jsonSchemas/cargo-manifest-schema.json")

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
}
