package com.github.sammyvimes.yamakeplugin

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.ForeignLeafPsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.cidr.lang.psi.OCMacroCall
import com.jetbrains.cidr.lang.psi.visitors.OCVisitor
import com.jetbrains.cidr.lang.util.OCElementUtil
import java.io.File

fun getYaUnitTestMacro(element: PsiElement): OCMacroCall? {
    if (element !is LeafPsiElement) {
        return null
    }

    val name: String? = OCElementUtil.getElementType(element.node).toString()

    if ("IDENTIFIER" != name) {
        return null
    }

    val parent: PsiElement? = element.parent

    if (parent == null) {
        return null
    }

    val grandpa = parent.parent
    if (grandpa == null) {
        return null
    }

    var macro: OCMacroCall? = null
    grandpa.accept(object : OCVisitor() {
        override fun visitMacroCall(call: OCMacroCall) {
            if (call.firstChild != null) {
                if ("Y_UNIT_TEST" == call.firstChild.text) {
                    macro = call
                } else if (call.firstChild.firstChild != null) {
                    if ("Y_UNIT_TEST" == call.firstChild.firstChild.text) {
                        macro = call
                    }
                }
            }
        }
    })

    if (macro == null) {
        return null
    }

    return macro
}
