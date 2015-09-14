// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.rust.lang.core.psi.RustCompositeElementTypes.*;
import org.rust.lang.core.psi.impl.mixin.RustPatIdentImplMixin;
import org.rust.lang.core.psi.*;

public class RustPatIdentImpl extends RustPatIdentImplMixin implements RustPatIdent {

  public RustPatIdentImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) ((RustVisitor)visitor).visitPatIdent(this);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public RustBindingMode getBindingMode() {
    return findChildByClass(RustBindingMode.class);
  }

  @Override
  @Nullable
  public RustPat getPat() {
    return findChildByClass(RustPat.class);
  }

  @Override
  @Nullable
  public PsiElement getAt() {
    return findChildByType(AT);
  }

  @Override
  @NotNull
  public PsiElement getIdentifier() {
    return findNotNullChildByType(IDENTIFIER);
  }

}
