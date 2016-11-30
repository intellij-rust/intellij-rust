package org.toml.ide

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import org.toml.lang.core.psi.TomlTypes.STRING

class TomlQuoteHandler : SimpleTokenSetQuoteHandler(STRING)
