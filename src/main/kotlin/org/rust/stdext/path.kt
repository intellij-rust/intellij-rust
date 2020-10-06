/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

@Throws(IOException::class)
fun Path.cleanDirectory(): Path = Files.walkFileTree(this, object : SimpleFileVisitor<Path>() {

    @Throws(IOException::class)
    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        try {
            Files.delete(file)
        } catch (ignored: IOException) {
        }
        return FileVisitResult.CONTINUE
    }

    @Throws(IOException::class)
    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
        if (dir != this@cleanDirectory) {
            try {
                Files.delete(dir)
            } catch (ignored: IOException) {
            }
        }
        return FileVisitResult.CONTINUE
    }
})

@Throws(IOException::class)
fun Path.newDeflaterDataOutputStream(): DataOutputStream =
    DataOutputStream(DeflaterOutputStream(Files.newOutputStream(this)))

@Throws(IOException::class)
fun Path.newInflaterDataInputStream(): DataInputStream =
    DataInputStream(InflaterInputStream(Files.newInputStream(this)))
