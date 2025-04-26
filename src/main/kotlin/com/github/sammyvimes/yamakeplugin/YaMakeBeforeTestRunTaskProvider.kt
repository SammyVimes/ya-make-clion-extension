package com.github.sammyvimes.yamakeplugin

import com.intellij.execution.*
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.RegexpFilter
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.tools.FilterInfo
import com.intellij.tools.ToolProcessAdapter
import com.intellij.tools.ToolRunProfile
import javax.swing.Icon

class YaMakeBeforeTestRunTaskProvider : BeforeRunTaskProvider<YaMakeBeforeRunTask>() {
    companion object {
        val ID: Key<YaMakeBeforeRunTask> = Key.create("YaMakeBeforeRunTask")

        fun getInstance(project: Project): YaMakeBeforeTestRunTaskProvider? {
            val instanceOf: Class<YaMakeBeforeTestRunTaskProvider> = YaMakeBeforeTestRunTaskProvider::class.java

            return EP_NAME.findExtension(instanceOf as Class<BeforeRunTaskProvider<BeforeRunTask<*>>>, project) as YaMakeBeforeTestRunTaskProvider?
        }
    }

    override fun getId(): Key<YaMakeBeforeRunTask> = ID
    override fun getName(): String = "Run ya make"
    override fun getIcon(): Icon = AllIcons.Actions.Compile

    override fun createTask(runConfiguration: RunConfiguration): YaMakeBeforeRunTask? {
        return YaMakeBeforeRunTask()
    }

    override fun executeTask(
        context: DataContext,
        configuration: RunConfiguration,
        environment: ExecutionEnvironment,
        task: YaMakeBeforeRunTask
    ): Boolean {
        val project = configuration.project
        val filePath = task.targetFile ?: return false
        val yaPath = YaSettings.getInstance().state.yaPath
        val commandLine = GeneralCommandLine(yaPath, "make", "-r", filePath)
        commandLine.setWorkDirectory(project.basePath)

        val environment: ExecutionEnvironment = ExecutionEnvironmentBuilder.create(project,
        DefaultRunExecutor.getRunExecutorInstance(),
            YaMakeRunProfile(commandLine)
        ).build(null)

        environment.runner.execute(environment)

        return true
    }
}

class YaMakeRunProfile(private val myCommandLine: GeneralCommandLine?) : RunProfile {

    override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState? {
        val project = env.getProject()

        if (myCommandLine == null) {
            // can return null if creation of cmd line has been cancelled
            return null
        }

        val commandLineState: CommandLineState = object : CommandLineState(env) {
            fun createCommandLine(): GeneralCommandLine {
                return myCommandLine
            }

            @Throws(ExecutionException::class)
            override fun startProcess(): OSProcessHandler {
                val commandLine = createCommandLine()
                val processHandler: OSProcessHandler = ColoredProcessHandler(commandLine)
                ProcessTerminatedListener.attach(processHandler)
                return processHandler
            }

//            @Throws(ExecutionException::class)
//            override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
//                val result = super.execute(executor, runner)
//                val processHandler = result.getProcessHandler()
//                if (processHandler != null) {
//                    processHandler.addProcessListener(
//                        ToolProcessAdapter(
//                            project,
//                            myTool.synchronizeAfterExecution(),
//                            getName()
//                        )
//                    )
//                    processHandler.addProcessListener(object : ProcessAdapter() {
//                        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
//                            if ((outputType === ProcessOutputTypes.STDOUT && myTool.isShowConsoleOnStdOut())
//                                || (outputType === ProcessOutputTypes.STDERR && myTool.isShowConsoleOnStdErr())
//                            ) {
//                                ApplicationManager.getApplication().invokeLater(Runnable {
//                                    RunContentManager.getInstance(project).toFrontRunContent(executor, processHandler)
//                                }, project.getDisposed())
//                            }
//                        }
//                    })
//                }
//                return result
//            }
        }
        val builder = TextConsoleBuilderFactory.getInstance().createBuilder(project)

        commandLineState.setConsoleBuilder(builder)

        return commandLineState
    }

    override fun getName(): @NlsSafe String {
        return "Ya Make"
    }

    override fun getIcon(): Icon? {
        return AllIcons.Actions.Compile
    }
}

class YaMakeBeforeRunTask : BeforeRunTask<YaMakeBeforeRunTask>(YaMakeBeforeTestRunTaskProvider.ID) {
    var targetFile: String? = null
}