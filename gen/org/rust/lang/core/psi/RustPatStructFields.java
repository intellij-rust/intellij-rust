// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustPatStructFields extends RustPat {

  @NotNull
  List<RustBindingMode> getBindingModeList();

  @NotNull
  List<RustPat> getPatList();

  @Nullable
  PsiElement getDotdot();

}
