package com.kraken.plugin.refactoring

import com.intellij.ide.util.TreeFileChooserFactory
import com.intellij.openapi.project.Project
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.lang.KrakenFileType

/** Dialogue de choix du fichier `.rules` de destination, commun aux handlers de déplacement. */
internal fun chooseMoveTarget(project: Project, source: KrakenFile, title: String): KrakenFile? {
    val chooser = TreeFileChooserFactory.getInstance(project).createFileChooser(
        title,
        source,
        KrakenFileType,
    ) { it is KrakenFile && it != source }
    chooser.showDialog()
    return chooser.selectedFile as? KrakenFile
}
