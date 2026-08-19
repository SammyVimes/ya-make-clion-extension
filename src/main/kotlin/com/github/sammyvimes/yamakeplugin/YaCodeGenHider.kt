package com.github.sammyvimes.yamakeplugin

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

class YaCodeGenHider : TreeStructureProvider {
    override fun modify(
        parent: AbstractTreeNode<*>,
        children: MutableCollection<AbstractTreeNode<*>>,
        settings: ViewSettings?
    ): MutableCollection<AbstractTreeNode<*>> {
        val project = parent.project ?: return children
        val grouped = children.toMutableList()

        if (parent.value !is Project && parent.value !is Module) return grouped

        // Collect all known generated folders
        val codegenPath = YaProjectSettings.getInstance(project).codegenPath().toAbsolutePath().toString()

        val fileSystem = LocalFileSystem.getInstance()

        val codegenFile = fileSystem.findFileByPath(codegenPath)

        if (codegenFile != null) {
            grouped.removeAll { node ->
                val vf = when (val value = node.value) {
                    is VirtualFile -> value
                    is PsiDirectory -> value.virtualFile
                    is PsiFile -> value.virtualFile
                    else -> null
                } ?: return@removeAll false

                return@removeAll codegenFile == vf || VfsUtilCore.isAncestor(codegenFile, vf, false)
            }

            if (parent.value is Project) {
                val codegenNode = CodegenRootNode(project, codegenFile, settings)
                grouped.add(codegenNode)
            }
        }

        return grouped
    }

    private fun shouldHide(file: VirtualFile, project: Project?): Boolean {
        if (project == null) return false
        val codegenPath = YaProjectSettings.getInstance(project).codegenPath().toAbsolutePath().toString()
        return file.path.startsWith(codegenPath)
    }
}

class CodegenRootNode(
    project: Project,
    private val folder: VirtualFile,
    viewSettings: ViewSettings?
) : ProjectViewNode<String>(project, "Codegen", viewSettings) {

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val directory = PsiManager.getInstance(myProject).findDirectory(folder) ?: return emptyList()
        return listOf(PsiDirectoryNode(myProject, directory, settings))
    }

    override fun contains(file: VirtualFile): Boolean {
        return VfsUtilCore.isAncestor(folder, file, false)
    }

    override fun getName() = "[codegen]"

    override fun update(presentation: PresentationData) {
        presentation.presentableText = "[codegen]"
    }

    override fun getVirtualFile(): VirtualFile? = null
}
