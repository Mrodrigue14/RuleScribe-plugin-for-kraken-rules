package com.kraken.plugin.lang

import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.NonNls

/**
 * Token name as shown in syntax errors.
 *
 * The Grammar-Kit template overrides `toString()` to prefix the class name. That helps
 * when debugging the parser, but an error listing fifteen expected tokens then repeated
 * "KrakenTokenType." fifteen times. This keeps [IElementType]'s behaviour: the bare
 * name, which the platform quotes.
 */
class KrakenTokenType(@NonNls debugName: String) : IElementType(debugName, KrakenLanguage)
