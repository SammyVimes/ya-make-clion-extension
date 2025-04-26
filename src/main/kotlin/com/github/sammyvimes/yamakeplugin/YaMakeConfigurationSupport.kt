package com.github.sammyvimes.yamakeplugin

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement

class YaMakeConfigurationSupport {
    companion object {
        val TEST_SCOPE_ELEMENT_KEY: Key<YaMakeTest> = Key.create<YaMakeTest>("YA_MAKE_TEST_ELEMENT_KEY")
    }

    fun findCachedTestObject(el: PsiElement): YaMakeTest? {
        val test = TEST_SCOPE_ELEMENT_KEY.get(el)
        return test
    }
}