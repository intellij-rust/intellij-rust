/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.jsonSchema

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import org.rust.RsBundle

class CargoTomlJsonSchemaProviderFactory : JsonSchemaProviderFactory, DumbAware {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        return listOf(CargoTomlJsonSchemaFileProvider())
    }
}

// Provides empty json schema for Cargo.toml files
// It's a temporarily hack not to use remote scheme for Cargo.toml from https://json.schemastore.org/cargo.json
// by providing own empty embedded scheme (embedded schemes have more priority than remote ones)
// because it suggests unexpected completion variants like `{}`
class CargoTomlJsonSchemaFileProvider : JsonSchemaFileProvider {
    override fun isAvailable(file: VirtualFile): Boolean = file.name == "Cargo.toml"
    override fun getName(): String = RsBundle.message("cargo.toml.schema")
    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
    override fun isUserVisible(): Boolean = false
    override fun getSchemaFile(): VirtualFile? {
        return JsonSchemaProviderFactory.getResourceFile(CargoTomlJsonSchemaFileProvider::class.java, SCHEMA_PATH)
    }

    companion object {
        const val SCHEMA_PATH: String = "/jsonSchema/cargo.toml-schema.json"
    }
}
