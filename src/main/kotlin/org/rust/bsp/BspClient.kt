package org.rust.bsp

import ch.epfl.scala.bsp4j.*
import com.intellij.openapi.diagnostic.logger

class BspClient : BuildClient {
    private val log = logger<BspClient>()

    override fun onBuildShowMessage(params: ShowMessageParams?) {
        println("onBuildShowMessage $params")
    }

    override fun onBuildLogMessage(params: LogMessageParams?) {
        println("onBuildLogMessage $params")
    }

    override fun onBuildTaskStart(params: TaskStartParams?) {
        println("onBuildTaskStart $params")
    }

    override fun onBuildTaskProgress(params: TaskProgressParams?) {
        println("onBuildTaskProgress $params")
    }

    override fun onBuildTaskFinish(params: TaskFinishParams?) {
        println("onBuildTaskFinish $params")
    }

    override fun onBuildPublishDiagnostics(params: PublishDiagnosticsParams?) {
        println("onBuildPublishDiagnostics $params")
    }

    override fun onBuildTargetDidChange(params: DidChangeBuildTarget?) {
        println("onBuildTargetDidChange $params")
    }
}
