// This is a generated file. Not intended for manual editing.
package org.toml.lang.core.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.toml.lang.core.psi.TomlTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import org.toml.lang.core.psi.*;

public class TomlValueImpl extends ASTWrapperPsiElement implements TomlValue {

  public TomlValueImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull TomlVisitor visitor) {
    visitor.visitValue(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof TomlVisitor) accept((TomlVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public TomlArray getArray() {
    return findChildByClass(TomlArray.class);
  }

  @Override
  @Nullable
  public PsiElement getBoolean() {
    return findChildByType(BOOLEAN);
  }

  @Override
  @Nullable
  public PsiElement getNumber() {
    return findChildByType(NUMBER);
  }

  @Override
  @Nullable
  public PsiElement getString() {
    return findChildByType(STRING);
  }

}
