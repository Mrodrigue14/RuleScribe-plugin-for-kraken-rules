package com.kraken.plugin.lang

import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.NonNls

/**
 * Le nom d'un token tel qu'il apparaît dans les erreurs de syntaxe.
 *
 * Le gabarit Grammar-Kit redéfinit `toString()` pour préfixer le nom par celui
 * de la classe — utile au débogage du parseur, illisible pour qui lit le
 * message : une erreur listant quinze tokens attendus portait quinze fois
 * « KrakenTokenType. ». On garde donc le comportement d'[IElementType], qui
 * rend le nom seul ; c'est la plateforme qui l'entoure de guillemets.
 */
class KrakenTokenType(@NonNls debugName: String) : IElementType(debugName, KrakenLanguage)
