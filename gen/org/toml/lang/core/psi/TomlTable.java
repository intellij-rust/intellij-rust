// This is a generated file. Not intended for manual editing.
package org.toml.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface TomlTable extends PsiElement {

  @NotNull
  TomlTableEntries getTableEntries();

  @NotNull
  TomlTableHeader getTableHeader();

}
