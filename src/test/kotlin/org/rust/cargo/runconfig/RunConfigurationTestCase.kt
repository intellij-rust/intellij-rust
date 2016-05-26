package org.rust.cargo.runconfig

//class RunConfigurationTestCase : RustWithToolchainTestCaseBase() {
//    override val dataPath = "src/test/resources/org/rust/cargo/runconfig/fixtures"
//
//    fun testApplicationConfiguration() = withProject("hello") {
//        val configuration = createConfiguration()
//        val result = execute(configuration)
//
//        assertThat(result.stdout).contains("Hello, world!")
//    }
//
//    private fun createConfiguration(): CargoCommandConfiguration {
//        val configurationType = ConfigurationTypeUtil.findConfigurationType(CargoCommandRunConfigurationType::class.java)
//        val factory = configurationType.configurationFactories[0]
//        val configuration = factory.createTemplateConfiguration(myModule.project) as CargoCommandConfiguration
//        configuration.setModule(myModule)
//        return configuration
//    }
//
//    private fun execute(configuration: RunConfiguration): ProcessOutput {
//        val executor = DefaultRunExecutor.getRunExecutorInstance()
//        val state = ExecutionEnvironmentBuilder
//            .create(executor, configuration)
//            .build()
//            .state!!
//
//        val result = state.execute(executor, RustRunner())!!
//
//        val listener = CapturingProcessAdapter()
//        with(result.processHandler) {
//            addProcessListener(listener)
//            startNotify()
//            waitFor()
//        }
//        Disposer.dispose(result.executionConsole)
//        return listener.output
//    }
//}
//
