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

public class RustDocImpl extends RustCompositeElementImpl implements RustDoc {

  public RustDocImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) ((RustVisitor)visitor).visitDoc(this);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public PsiElement getBlockDocComment() {
    return findChildByType(BLOCK_DOC_COMMENT);
  }

  @Override
  @Nullable
  public PsiElement getEolDocComment() {
    return findChildByType(EOL_DOC_COMMENT);
  }

}
