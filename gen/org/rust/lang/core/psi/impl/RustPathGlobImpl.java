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

public class RustPathGlobImpl extends RustCompositeElementImpl implements RustPathGlob {

  public RustPathGlobImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RustVisitor visitor) {
    visitor.visitPathGlob(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) accept((RustVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<RustPath> getPathList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustPath.class);
  }

  @Override
  @Nullable
  public RustPathGlob getPathGlob() {
    return findChildByClass(RustPathGlob.class);
  }

  @Override
  @Nullable
  public PsiElement getColoncolon() {
    return findChildByType(COLONCOLON);
  }

  @Override
  @Nullable
  public PsiElement getIdentifier() {
    return findChildByType(IDENTIFIER);
  }

  @Override
  @Nullable
  public PsiElement getLbrace() {
    return findChildByType(LBRACE);
  }

  @Override
  @Nullable
  public PsiElement getMul() {
    return findChildByType(MUL);
  }

  @Override
  @Nullable
  public PsiElement getRbrace() {
    return findChildByType(RBRACE);
  }

}
