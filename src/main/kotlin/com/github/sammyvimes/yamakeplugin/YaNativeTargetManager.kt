package com.github.sammyvimes.yamakeplugin

import com.intellij.openapi.project.Project
import com.jetbrains.cidr.cpp.execution.external.build.CLionExternalBuildConfiguration
import com.jetbrains.cidr.cpp.execution.external.build.CLionExternalBuildManager
import com.jetbrains.cidr.cpp.execution.external.build.CLionExternalBuildTarget
import com.jetbrains.cidr.cpp.execution.external.build.CLionExternalConfiguration
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import java.nio.charset.StandardCharsets
import java.util.UUID

object YaNativeTargetManager {
    private const val TARGET_NAME = "Ya prepared binary"
    private const val CONFIGURATION_NAME = "Debug"

    fun ensure(project: Project): Pair<CLionExternalBuildTarget, CLionExternalBuildConfiguration> {
        val manager = CLionExternalBuildManager.getInstance(project)
        manager.targets.firstOrNull { it.id == TARGET_ID }?.let { existing ->
            return existing to requireNotNull(existing.defaultConfiguration())
        }

        val toolchainName = CPPToolchains.getInstance().defaultToolchain?.name
            ?: CPPToolchains.Toolchain.getDefault()
        val buildConfiguration = CLionExternalBuildConfiguration(
            CONFIGURATION_NAME,
            null,
            null,
            toolchainName,
            CONFIGURATION_ID,
        )
        val target = CLionExternalBuildTarget(
            TARGET_NAME,
            project.name,
            listOf(buildConfiguration),
            CLionExternalConfiguration.Type.Tool,
            TARGET_ID,
        )
        manager.targets = manager.targets + target
        return target to buildConfiguration
    }

    private fun stableId(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))

    private val TARGET_ID = stableId("ya-make-clion:prepared-binary-target")
    private val CONFIGURATION_ID = stableId("ya-make-clion:debug-configuration")
}
