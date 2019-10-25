/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.rustSlowTests.cargo.runconfig.buildtool

import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FailureResultImpl
import com.intellij.build.events.impl.SuccessResultImpl
import org.rust.MinRustcVersion
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.mockProgressIndicator
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.testBuildId
import org.rust.fileTree
import org.rustSlowTests.cargo.runconfig.buildtool.CargoBuildTest.Companion.MyFinishBuildEvent
import org.rustSlowTests.cargo.runconfig.buildtool.CargoBuildTest.Companion.MyFinishEvent
import org.rustSlowTests.cargo.runconfig.buildtool.CargoBuildTest.Companion.MyMessageEvent
import org.rustSlowTests.cargo.runconfig.buildtool.CargoBuildTest.Companion.MyStartBuildEvent
import org.rustSlowTests.cargo.runconfig.buildtool.CargoBuildTest.Companion.MyStartEvent

@MinRustcVersion("1.32.0")
class CargoBuildManagerTest : CargoBuildTest() {

    fun `test build successful`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {}
                """)
            }
        }.create()
        val buildResult = buildProject()

        checkResult(
            buildResult,
            message = "Build finished",
            errors = 0,
            warnings = 0,
            succeeded = true,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0"
            ),
            MyFinishEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0",
                result = SuccessResultImpl()
            ),
            MyFinishBuildEvent(
                message = "Build successful",
                result = SuccessResultImpl()
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... project(bin)"
        )
    }

    fun `test build successful with warning`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    fn foo() {}
                """)
            }
        }.create()
        val buildResult = buildProject()

        checkResult(
            buildResult,
            message = "Build finished",
            errors = 0,
            warnings = 1,
            succeeded = true,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0"
            ),
            MyMessageEvent(
                parentId = "project 0.1.0",
                message = "Function is never used: `foo`",
                kind = MessageEvent.Kind.WARNING
            ),
            MyFinishEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0",
                result = SuccessResultImpl()
            ),
            MyFinishBuildEvent(
                message = "Build successful",
                result = SuccessResultImpl()
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... project"
        )
    }

    fun `test build failed`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    fn foo() { 0 }
                """)
            }
        }.create()
        val buildResult = buildProject()

        checkResult(
            buildResult,
            message = "Build failed",
            errors = 1,
            warnings = 0,
            succeeded = false,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0"
            ),
            MyMessageEvent(
                parentId = "project 0.1.0",
                message = "Mismatched types",
                kind = MessageEvent.Kind.ERROR
            ),
            MyFinishEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0",
                result = FailureResultImpl(null as Throwable?)
            ),
            MyFinishBuildEvent(
                message = "Build failed",
                result = FailureResultImpl(null as Throwable?)
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... project"
        )
    }

    fun `test build failed (multiple errors)`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    fn foo() {
                        1 + "1";
                        2 + "2";
                        3 + "3";
                    }
                """)
            }
        }.create()
        val buildResult = buildProject()

        checkResult(
            buildResult,
            message = "Build failed",
            errors = 3,
            warnings = 0,
            succeeded = false,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0"
            ),
            MyMessageEvent(
                parentId = "project 0.1.0",
                message = "Cannot add `&str` to `{integer}`",
                kind = MessageEvent.Kind.ERROR
            ),
            MyMessageEvent(
                parentId = "project 0.1.0",
                message = "Cannot add `&str` to `{integer}`",
                kind = MessageEvent.Kind.ERROR
            ),
            MyMessageEvent(
                parentId = "project 0.1.0",
                message = "Cannot add `&str` to `{integer}`",
                kind = MessageEvent.Kind.ERROR
            ),
            MyFinishEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0",
                result = FailureResultImpl(null as Throwable?)
            ),
            MyFinishBuildEvent(
                message = "Build failed",
                result = FailureResultImpl(null as Throwable?)
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... project"
        )
    }

    fun `test build canceled`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    fn _foo() {}
                """)
            }
        }.create()
        mockProgressIndicator?.cancel()
        val buildResult = buildProject()

        checkResult(
            buildResult,
            message = "Build canceled",
            errors = 0,
            warnings = 0,
            succeeded = false,
            canceled = true
        )

        checkEvents()

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish..."
        )
    }

    fun `test build errors in multiple files`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("foo.rs", """
                    fn foo() { 0 }
                """)
                rust("bar.rs", """
                    fn bar() { 0 }
                """)
                rust("lib.rs", """
                    mod foo;
                    mod bar;
                """)
            }
        }.create()
        val buildResult = buildProject()

        checkResult(
            buildResult,
            message = "Build failed",
            errors = 2,
            warnings = 0,
            succeeded = false,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0"
            ),
            MyMessageEvent(
                parentId = "project 0.1.0",
                message = "Mismatched types",
                kind = MessageEvent.Kind.ERROR
            ),
            MyMessageEvent(
                parentId = "project 0.1.0",
                message = "Mismatched types",
                kind = MessageEvent.Kind.ERROR
            ),
            MyFinishEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0",
                result = FailureResultImpl(null as Throwable?)
            ),
            MyFinishBuildEvent(
                message = "Build failed",
                result = FailureResultImpl(null as Throwable?)
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... project"
        )
    }

    fun `test build lib successful bin successful`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    fn _foo() {}
                """)
                rust("main.rs", """
                    fn main() {}
                """)
            }
        }.create()
        val buildResult = buildProject()

        checkResult(
            buildResult,
            message = "Build finished",
            errors = 0,
            warnings = 0,
            succeeded = true,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0"
            ),
            MyFinishEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0",
                result = SuccessResultImpl()
            ),
            MyFinishBuildEvent(
                message = "Build successful",
                result = SuccessResultImpl()
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... project",
            "Building... project(bin)"
        )
    }

    fun `test build lib successful bin failed`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    fn _foo() {}
                """)
                rust("main.rs", """
                    fn main() { 0 }
                """)
            }
        }.create()
        val buildResult = buildProject()

        checkResult(
            buildResult,
            message = "Build failed",
            errors = 1,
            warnings = 0,
            succeeded = false,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0"
            ),
            MyMessageEvent(
                parentId = "project 0.1.0",
                message = "Mismatched types",
                kind = MessageEvent.Kind.ERROR
            ),
            MyFinishEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0",
                result = FailureResultImpl(null as Throwable?)
            ),
            MyFinishBuildEvent(
                message = "Build failed",
                result = FailureResultImpl(null as Throwable?)
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... project",
            "Building... project(bin)"
        )
    }

    fun `test build lib failed bin skipped`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    fn foo() { 0 }
                """)
                rust("main.rs", """
                    fn main() { 0 }
                """)
            }
        }.create()
        val buildResult = buildProject()

        checkResult(
            buildResult,
            message = "Build failed",
            errors = 1,
            warnings = 0,
            succeeded = false,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0"
            ),
            MyMessageEvent(
                parentId = "project 0.1.0",
                message = "Mismatched types",
                kind = MessageEvent.Kind.ERROR
            ),
            MyFinishEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0",
                result = FailureResultImpl(null as Throwable?)
            ),
            MyFinishBuildEvent(
                message = "Build failed",
                result = FailureResultImpl(null as Throwable?)
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... project"
        )
    }

    fun `test build multiple packages first successful second successful`() {
        fileTree {
            toml("Cargo.toml", """
                [workspace]
                members = [
                    "first",
                    "second",
                ]
            """)

            dir("first") {
                toml("Cargo.toml", """
                    [package]
                    name = "first"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("lib.rs", """
                    fn _foo() {}
                """)
                }
            }

            dir("second") {
                toml("Cargo.toml", """
                    [package]
                    name = "second"
                    version = "0.1.0"
                    authors = []

                    [dependencies]
                    first = { path = "../first" }
                """)

                dir("src") {
                    rust("lib.rs", """
                    fn _bar() {}
                """)
                }
            }
        }.create()
        val buildResult = buildProject()

        checkResult(
            buildResult,
            message = "Build finished",
            errors = 0,
            warnings = 0,
            succeeded = true,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "first 0.1.0",
                parentId = testBuildId,
                message = "Compiling first v0.1.0"
            ),
            MyStartEvent(
                id = "second 0.1.0",
                parentId = testBuildId,
                message = "Compiling second v0.1.0"
            ),
            MyFinishEvent(
                id = "first 0.1.0",
                parentId = testBuildId,
                message = "Compiling first v0.1.0",
                result = SuccessResultImpl()
            ),
            MyFinishEvent(
                id = "second 0.1.0",
                parentId = testBuildId,
                message = "Compiling second v0.1.0",
                result = SuccessResultImpl()
            ),
            MyFinishBuildEvent(
                message = "Build successful",
                result = SuccessResultImpl()
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... first",
            "Building... second"
        )
    }

    fun `test build multiple packages first successful second failed`() {
        fileTree {
            toml("Cargo.toml", """
                [workspace]
                members = [
                    "first",
                    "second",
                ]
            """)

            dir("first") {
                toml("Cargo.toml", """
                    [package]
                    name = "first"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("lib.rs", """
                    fn _foo() {}
                """)
                }
            }

            dir("second") {
                toml("Cargo.toml", """
                    [package]
                    name = "second"
                    version = "0.1.0"
                    authors = []

                    [dependencies]
                    first = { path = "../first" }
                """)

                dir("src") {
                    rust("lib.rs", """
                    fn _bar() { 0 }
                """)
                }
            }
        }.create()
        val buildResult = buildProject()

        checkResult(
            buildResult,
            message = "Build failed",
            errors = 1,
            warnings = 0,
            succeeded = false,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "first 0.1.0",
                parentId = testBuildId,
                message = "Compiling first v0.1.0"
            ),
            MyStartEvent(
                id = "second 0.1.0",
                parentId = testBuildId,
                message = "Compiling second v0.1.0"
            ),
            MyFinishEvent(
                id = "first 0.1.0",
                parentId = testBuildId,
                message = "Compiling first v0.1.0",
                result = SuccessResultImpl()
            ),
            MyMessageEvent(
                parentId = "second 0.1.0",
                message = "Mismatched types",
                kind = MessageEvent.Kind.ERROR
            ),
            MyFinishEvent(
                id = "second 0.1.0",
                parentId = testBuildId,
                message = "Compiling second v0.1.0",
                result = FailureResultImpl(null as Throwable?)
            ),
            MyFinishBuildEvent(
                message = "Build failed",
                result = FailureResultImpl(null as Throwable?)
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... first",
            "Building... second"
        )
    }

    fun `test build multiple packages first failed second skipped`() {
        fileTree {
            toml("Cargo.toml", """
                [workspace]
                members = [
                    "first",
                    "second",
                ]
            """)

            dir("first") {
                toml("Cargo.toml", """
                    [package]
                    name = "first"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("lib.rs", """
                    fn foo() { 0 }
                """)
                }
            }

            dir("second") {
                toml("Cargo.toml", """
                    [package]
                    name = "second"
                    version = "0.1.0"
                    authors = []

                    [dependencies]
                    first = { path = "../first" }
                """)

                dir("src") {
                    rust("lib.rs", """
                    fn _bar() { 0 }
                """)
                }
            }
        }.create()
        val buildResult = buildProject()

        checkResult(
            buildResult,
            message = "Build failed",
            errors = 1,
            warnings = 0,
            succeeded = false,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "first 0.1.0",
                parentId = testBuildId,
                message = "Compiling first v0.1.0"
            ),
            MyMessageEvent(
                parentId = "first 0.1.0",
                message = "Mismatched types",
                kind = MessageEvent.Kind.ERROR
            ),
            MyFinishEvent(
                id = "first 0.1.0",
                parentId = testBuildId,
                message = "Compiling first v0.1.0",
                result = FailureResultImpl(null as Throwable?)
            ),
            MyFinishBuildEvent(
                message = "Build failed",
                result = FailureResultImpl(null as Throwable?)
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... first"
        )
    }
}
