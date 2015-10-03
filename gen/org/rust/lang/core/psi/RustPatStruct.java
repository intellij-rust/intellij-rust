// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustPatStruct extends RustPat {

  @NotNull
  RustPatStructFields getPatStructFields();

  @NotNull
  RustPathWithColons getPathWithColons();

  @NotNull
  PsiElement getLbrace();

  @NotNull
  PsiElement getRbrace();

}
