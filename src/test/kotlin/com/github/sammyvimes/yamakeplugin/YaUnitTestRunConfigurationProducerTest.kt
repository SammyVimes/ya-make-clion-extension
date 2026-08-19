package com.github.sammyvimes.yamakeplugin

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.RunManagerEx
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.cidr.cpp.execution.external.run.CLionExternalRunConfiguration
import com.jetbrains.cidr.cpp.execution.external.run.CLionExternalRunConfigurationType
import java.nio.file.Paths

class YaUnitTestRunConfigurationProducerTest : BasePlatformTestCase() {
    override fun tearDown() {
        // Nova's test startup toggles this application-level setting asynchronously.
        CodeInsightSettings.getInstance().AUTO_POPUP_JAVADOC_INFO = false
        super.tearDown()
    }

    fun testNovaMarkerUsesExactlyOneYaRunConfiguration() {
        myFixture.addFileToProject("ya.make", "UNITTEST()")
        val file = myFixture.configureByText(
            FileTypeManager.getInstance().getFileTypeByExtension("cpp"),
            """
            Y_UNIT_TEST_SUITE(TSampleSuite) {
                Y_UNIT_TEST(TestOne) {
                }
            }
            """.trimIndent(),
        )
        assertEquals("C++", file.language.id)

        val macroOffset = file.text.indexOf("Y_UNIT_TEST(TestOne)")
        val macroLeaf = file.findElementAt(macroOffset)
        assertNotNull(macroLeaf)

        myFixture.doHighlighting()
        val gutters = myFixture.findAllGutters()
        assertEquals("All gutter tooltips: ${gutters.map { it.tooltipText }}", 1, gutters.size)
        assertTrue(gutters.single().tooltipText.orEmpty().contains("Run Ya test"))

        val configurations = ConfigurationContext(macroLeaf!!).configurationsFromContext.orEmpty()
        val yaConfigurations = configurations.filter {
            it.isProducedBy(YaUnitTestRunConfigurationProducer::class.java)
        }
        assertEquals(1, yaConfigurations.size)
        assertEquals("Ya Test: TSampleSuite::TestOne", yaConfigurations.single().configuration.name)
    }

    fun testMalformedMacroHasNoYaGutter() {
        myFixture.addFileToProject("ya.make", "UNITTEST()")
        myFixture.configureByText(
            FileTypeManager.getInstance().getFileTypeByExtension("cpp"),
            "Y_ UNIT_TEST(TestOne) {}",
        )

        myFixture.doHighlighting()

        assertEmpty(myFixture.findAllGutters())
    }

    fun testFallbackIsSuppressedWhenClionHasNativeMarker() {
        myFixture.addFileToProject("ya.make", "UNITTEST()")
        val file = myFixture.configureByText(
            FileTypeManager.getInstance().getFileTypeByExtension("cpp"),
            "Y_UNIT_TEST(TestOne) {}",
        )
        val macroLeaf = requireNotNull(file.findElementAt(0))
        val nativeContributor = object : RunLineMarkerContributor() {
            override fun getInfo(element: PsiElement): Info? =
                element.takeIf { it === macroLeaf }?.let {
                    Info(AllIcons.RunConfigurations.TestState.Run, ExecutorAction.getActions())
                }
        }

        RunLineMarkerContributor.EXTENSION.addExplicitExtension(file.language, nativeContributor)
        try {
            val fallback = ProgressManager.getInstance().runProcess(
                Computable { YaUnitTestRunLineMarkerContributor().getInfo(macroLeaf) },
                EmptyProgressIndicator(),
            )
            assertNull(fallback)
        } finally {
            RunLineMarkerContributor.EXTENSION.removeExplicitExtension(file.language, nativeContributor)
        }
    }

    fun testResolvedConfigurationRunsPreparedBinaryDirectly() {
        val factory = ConfigurationTypeUtil.findConfigurationType(CLionExternalRunConfigurationType::class.java)
            .configurationFactories.first()
        val configuration = factory.createTemplateConfiguration(project) as CLionExternalRunConfiguration
        val metadata = YaTestMetadata(
            targetPath = Paths.get("/arcadia/ydb/example/ut"),
            binaryPath = Paths.get("/arcadia/ydb/example/ut/example-ut"),
            sourcePath = Paths.get("/arcadia/ydb/example/ut"),
            workingDirectory = Paths.get("/arcadia/ydb/example/ut/test-results/unittest"),
            contextFile = Paths.get("/arcadia/ydb/example/ut/test-results/unittest/test.context"),
            testName = "example-ut",
            size = "SMALL",
        )

        YaUnitTestRunConfigurationProducer().configureResolvedTest(
            configuration,
            "TSampleSuite::TestOne",
            metadata,
        )

        assertEquals(metadata.binaryPath.toString(), configuration.executableData?.path)
        assertEquals("TSampleSuite::TestOne", configuration.programParameters)
        assertEquals(metadata.workingDirectory.toString(), configuration.workingDirectory)
        assertEquals(mapOf("YA_TEST_CONTEXT_FILE" to metadata.contextFile.toString()), configuration.envs)
        assertNotNull(configuration.targetAndConfigurationData)

        val prepareTasks = RunManagerEx.getInstanceEx(project)
            .getBeforeRunTasks(configuration)
            .filterIsInstance<YaPrepareTestTask>()
        assertEquals(1, prepareTasks.size)
        assertEquals(metadata.targetPath.toString(), prepareTasks.single().targetPath)
    }
}
