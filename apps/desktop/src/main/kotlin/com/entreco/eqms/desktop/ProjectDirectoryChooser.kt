package com.entreco.eqms.desktop

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JPanel

internal fun createProjectFolder(parent: Path, name: String): Path {
    require(
        name.isNotBlank() && name != "." && name != ".." &&
            '/' !in name && '\\' !in name && ':' !in name &&
            name.none { it.code < 32 } &&
            !Path.of(name).isAbsolute,
    ) { "Enter a single folder name, not a path." }
    return Files.createDirectory(parent.resolve(name))
}

internal fun projectDirectoryChooser(operation: WorkspaceOperation): JFileChooser =
    JFileChooser().apply {
        dialogTitle = operation.label
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
        if (operation == WorkspaceOperation.Create) {
            val chooser = this
            accessory = JPanel().apply {
                add(JButton("New folder").apply {
                    addActionListener {
                        val name = JOptionPane.showInputDialog(
                            chooser,
                            "Folder name:",
                            "New folder",
                            JOptionPane.PLAIN_MESSAGE,
                        ) ?: return@addActionListener
                        try {
                            val folder = createProjectFolder(chooser.currentDirectory.toPath(), name)
                            chooser.rescanCurrentDirectory()
                            chooser.selectedFile = folder.toFile()
                            chooser.ensureFileIsVisible(folder.toFile())
                        } catch (error: IllegalArgumentException) {
                            showFolderError(chooser, error)
                        } catch (error: IOException) {
                            showFolderError(chooser, error)
                        } catch (error: SecurityException) {
                            showFolderError(chooser, error)
                        }
                    }
                })
            }
        }
    }

private fun showFolderError(chooser: JFileChooser, error: Exception) {
    JOptionPane.showMessageDialog(
        chooser,
        "Could not create folder: ${error.message ?: error.javaClass.simpleName}",
        "New folder",
        JOptionPane.ERROR_MESSAGE,
    )
}
