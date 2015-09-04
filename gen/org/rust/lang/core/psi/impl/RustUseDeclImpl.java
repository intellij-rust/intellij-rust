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

public class RustUseDeclImpl extends RustCompositeElementImpl implements RustUseDecl {

  public RustUseDeclImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) ((RustVisitor)visitor).visitUseDecl(this);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public RustPath getPath() {
    return findChildByClass(RustPath.class);
  }

  @Override
  @Nullable
  public RustPathGlob getPathGlob() {
    return findChildByClass(RustPathGlob.class);
  }

  @Override
  @Nullable
  public PsiElement getAs() {
    return findChildByType(AS);
  }

  @Override
  @Nullable
  public PsiElement getPriv() {
    return findChildByType(PRIV);
  }

  @Override
  @Nullable
  public PsiElement getPub() {
    return findChildByType(PUB);
  }

  @Override
  @NotNull
  public PsiElement getUse() {
    return findNotNullChildByType(USE);
  }

  @Override
  @Nullable
  public PsiElement getIdentifier() {
    return findChildByType(IDENTIFIER);
  }

}
