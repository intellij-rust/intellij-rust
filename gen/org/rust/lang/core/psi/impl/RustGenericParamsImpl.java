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

public class RustGenericParamsImpl extends RustCompositeElementImpl implements RustGenericParams {

  public RustGenericParamsImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) ((RustVisitor)visitor).visitGenericParams(this);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public RustLifetimes getLifetimes() {
    return findChildByClass(RustLifetimes.class);
  }

  @Override
  @Nullable
  public RustTypeParam getTypeParam() {
    return findChildByClass(RustTypeParam.class);
  }

  @Override
  @Nullable
  public PsiElement getGt() {
    return findChildByType(GT);
  }

  @Override
  @Nullable
  public PsiElement getLt() {
    return findChildByType(LT);
  }

}
