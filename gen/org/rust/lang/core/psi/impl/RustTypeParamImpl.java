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

public class RustTypeParamImpl extends RustCompositeElementImpl implements RustTypeParam {

  public RustTypeParamImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) ((RustVisitor)visitor).visitTypeParam(this);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public RustTypeParamBounds getTypeParamBounds() {
    return findChildByClass(RustTypeParamBounds.class);
  }

  @Override
  @Nullable
  public RustTypeParamDefault getTypeParamDefault() {
    return findChildByClass(RustTypeParamDefault.class);
  }

  @Override
  @Nullable
  public PsiElement getQ() {
    return findChildByType(Q);
  }

}
