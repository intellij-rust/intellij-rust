/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.util.net.HttpConfigurable
import org.rust.cargo.RustWithToolchainTestBase
import org.rust.cargo.project.settings.toolchain
import org.rust.fileTree
import org.rust.openapiext.pathAsPath

class CargoProxyTest : RustWithToolchainTestBase() {

    fun testProxyEnv() {

        val httpSettings = HttpConfigurable.getInstance();
        httpSettings.USE_HTTP_PROXY = true
        httpSettings.PROXY_AUTHENTICATION = true
        httpSettings.PROXY_HOST = "host"
        httpSettings.PROXY_PORT = 3268
        httpSettings.proxyLogin = "user"
        httpSettings.plainProxyPassword = "pwd"


        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {
                    println!("Hello, world!");
                    }
                """)
            }
        }.create()

        val cargo = myModule.project.toolchain!!.cargo(cargoProjectDirectory.pathAsPath)
        val commandLine = CargoCommandLine("check");
        val command = cargo.toGeneralCommandLine(commandLine);

        check("http_proxy" in command.environment && command.environment["http_proxy"] == "http://user:pwd@host:3268/" );

    }
}
