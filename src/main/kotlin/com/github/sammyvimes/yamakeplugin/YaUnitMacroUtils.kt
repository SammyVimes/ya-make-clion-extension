package com.github.sammyvimes.yamakeplugin

import com.intellij.psi.PsiElement

private const val YA_UNIT_TEST_MACRO = "Y_UNIT_TEST"
private val YA_UNIT_TEST_PATTERN = Regex(
    """Y_UNIT_TEST\s*\(\s*([A-Za-z_][A-Za-z0-9_:]*)\s*\)""",
)
private val YA_UNIT_TEST_SUITE_PATTERN = Regex(
    """Y_UNIT_TEST_SUITE\s*\(\s*([A-Za-z_][A-Za-z0-9_:]*)\s*\)""",
)

internal data class YaUnitTestCall(
    val testName: String,
    val fullName: String,
)

fun getYaUnitTestMacro(element: PsiElement): PsiElement? {
    if (element.firstChild != null || element.text != YA_UNIT_TEST_MACRO) return null
    val fileText = element.containingFile?.text ?: return null
    return element.takeIf { findYaUnitTestCall(fileText, element.textOffset) != null }
}

fun getYaUnitTestName(element: PsiElement): String? {
    val fileText = element.containingFile?.text ?: return null
    return findYaUnitTestCall(fileText, element.textOffset)?.testName
}

fun getYaUnitTestFullName(element: PsiElement): String? {
    val fileText = element.containingFile?.text ?: return null
    return findYaUnitTestCall(fileText, element.textOffset)?.fullName
}

internal fun findYaUnitTestCall(fileText: String, offset: Int): YaUnitTestCall? {
    if (offset !in 0..fileText.length) return null
    val test = YA_UNIT_TEST_PATTERN.find(fileText, offset)
        ?.takeIf { it.range.first == offset }
        ?: return null
    val testName = test.groupValues[1]
    val prefix = fileText.substring(0, offset)
    val suiteName = YA_UNIT_TEST_SUITE_PATTERN.findAll(prefix).lastOrNull()?.groupValues?.get(1)
    val fullName = if (suiteName.isNullOrBlank()) testName else "$suiteName::$testName"
    return YaUnitTestCall(testName, fullName)
}
