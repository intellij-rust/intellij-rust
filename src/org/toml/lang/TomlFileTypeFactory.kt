package org.toml.lang

import com.intellij.openapi.fileTypes.FileTypeConsumer
import com.intellij.openapi.fileTypes.FileTypeFactory


/**
 * @author Aleksey.Kladov
 */
public class TomlFileTypeFactory : FileTypeFactory() {

    override fun createFileTypes(consumer: FileTypeConsumer) {
        consumer.consume(TomlFileType.INSTANCE, TomlFileType.DEFAULTS.EXTENSION);
    }

}