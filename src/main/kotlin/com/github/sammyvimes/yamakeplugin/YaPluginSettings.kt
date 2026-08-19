package com.github.sammyvimes.yamakeplugin

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import com.intellij.util.execution.ParametersListUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@State(
    name = "YaProjectSettings",
    storages = [Storage("yaMake.xml")],
)
@Service(Service.Level.PROJECT)
class YaProjectSettings(private val project: Project) : PersistentStateComponent<YaProjectSettings.Data> {
    data class Data(
        var yaPath: String = "",
        var targetPath: String = "",
        var codegenPath: String = "",
        var buildType: String = "",
        var extraYaArgs: String = DEFAULT_EXTRA_ARGS,
    )

    private var data = Data()

    override fun getState(): Data = data

    override fun loadState(state: Data) {
        data = state
    }

    fun projectRoot(): Path = Paths.get(requireNotNull(project.basePath) { "Project has no base path" })

    fun yaPath(): Path {
        if (data.yaPath.isNotBlank()) {
            return Paths.get(data.yaPath)
        }

        val repositoryYa = projectRoot().resolve("ya")
        return if (Files.isExecutable(repositoryYa)) repositoryYa else Paths.get("ya")
    }

    fun codegenPath(): Path = if (data.codegenPath.isNotBlank()) {
        Paths.get(data.codegenPath)
    } else {
        project.getProjectDataPath("ya-codegen")
    }

    fun compileCommandsPath(): Path = project.getProjectDataPath("ya-compdb").resolve("compile_commands.json")

    fun buildType(): String = data.buildType.ifBlank { DEFAULT_BUILD_TYPE }

    fun extraYaArgs(): String = data.extraYaArgs

    // Build configuration shared by every ya invocation that builds or inspects targets.
    // All call sites must agree, otherwise the plugin builds a parallel universe of the
    // user's cache (see the relwithdebinfo-vs-debug incident).
    fun configurationArgs(): List<String> =
        listOf("--build=${buildType()}") + ParametersListUtil.parse(data.extraYaArgs)

    fun selectedTarget(): Path? = data.targetPath.takeIf(String::isNotBlank)?.let(Paths::get)

    fun selectTarget(target: Path) {
        data.targetPath = target.toAbsolutePath().normalize().toString()
    }

    companion object {
        const val DEFAULT_BUILD_TYPE = "debug"

        // Mirrors the flag pack of the "Prepare test" / "Codegen" tasks that
        // `ya ide vscode` generates: the plugin must build the same configuration
        // universe the user's workspace cache is built with, otherwise every
        // before-run build is a cold rebuild.
        const val DEFAULT_EXTRA_ARGS = "-DBUILD_LANGUAGES=CPP -DCONSISTENT_DEBUG=yes" +
            " -DOPENSOURCE=yes -DUSE_PREBUILT_TOOLS=no -DAPPLE_SDK_LOCAL=yes -DUSE_CLANG_CL=yes" +
            " -DUSE_AIO=static -DUSE_ICONV=static -DUSE_IDN=static" +
            " \"-DCFLAGS=-fno-omit-frame-pointer -Wno-unknown-argument\""

        fun getInstance(project: Project): YaProjectSettings = project.service()
    }
}
