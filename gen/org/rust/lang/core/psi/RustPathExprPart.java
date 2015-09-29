// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustPathExprPart extends RustCompositeElement {

  @Nullable
  RustGenericArgs getGenericArgs();

  @Nullable
  RustPathExprPart getPathExprPart();

  @Nullable
  PsiElement getColoncolon();

  @Nullable
  PsiElement getCself();

  @Nullable
  PsiElement getIdentifier();

}
