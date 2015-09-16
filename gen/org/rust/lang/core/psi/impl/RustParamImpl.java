// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.rust.lang.core.psi.RustCompositeElementTypes.*;
import org.rust.lang.core.psi.*;

public class RustParamImpl extends RustCompositeElementImpl implements RustParam {

  public RustParamImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RustVisitor visitor) {
    visitor.visitParam(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) accept((RustVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public RustPat getPat() {
    return findNotNullChildByClass(RustPat.class);
  }

  @Override
  @NotNull
  public RustTypeSum getTypeSum() {
    return findNotNullChildByClass(RustTypeSum.class);
  }

  @Override
  @NotNull
  public PsiElement getColon() {
    return findNotNullChildByType(COLON);
  }

}
