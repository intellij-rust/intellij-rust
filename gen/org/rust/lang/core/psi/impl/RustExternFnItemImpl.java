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

public class RustExternFnItemImpl extends RustNamedElementImpl implements RustExternFnItem {

  public RustExternFnItemImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RustVisitor visitor) {
    visitor.visitExternFnItem(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) accept((RustVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public RustAbi getAbi() {
    return findChildByClass(RustAbi.class);
  }

  @Override
  @NotNull
  public RustFnItem getFnItem() {
    return findNotNullChildByClass(RustFnItem.class);
  }

  @Override
  @NotNull
  public PsiElement getExtern() {
    return findNotNullChildByType(EXTERN);
  }

}
