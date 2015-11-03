package org.toml.lang

import com.intellij.openapi.fileTypes.FileTypeConsumer
import com.intellij.openapi.fileTypes.FileTypeFactory


public class TomlFileTypeFactory : FileTypeFactory() {

    override fun createFileTypes(consumer: FileTypeConsumer) {
        consumer.consume(TomlFileType.INSTANCE, TomlFileType.INSTANCE.defaultExtension);
    }

}
