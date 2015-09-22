// This is a generated file. Not intended for manual editing.
package org.toml.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface TomlValue extends PsiElement {

  @Nullable
  TomlArray getArray();

  @Nullable
  PsiElement getBoolean();

  @Nullable
  PsiElement getNumber();

  @Nullable
  PsiElement getString();

}
