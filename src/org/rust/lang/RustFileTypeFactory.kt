package org.rust.lang

import com.intellij.openapi.fileTypes.FileTypeConsumer
import com.intellij.openapi.fileTypes.FileTypeFactory

public class RustFileTypeFactory : FileTypeFactory() {

    override fun createFileTypes(consumer: FileTypeConsumer) {
        consumer.consume(RustFileType, RustFileType.DEFAULTS.EXTENSION);
    }

}
