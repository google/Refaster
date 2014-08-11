/*
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.refaster;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.TreeVisitor;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;

import java.util.List;

import javax.annotation.Nullable;

/**
 * {@link UTree} version of {@link ParameterizedTypeTree}. This is the AST version of
 * {@link UClassType}.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
public abstract class UTypeApply extends UExpression implements ParameterizedTypeTree {
  public static UTypeApply create(UExpression type, List<UExpression> typeArguments) {
    return new AutoValue_UTypeApply(type, ImmutableList.copyOf(typeArguments));
  }

  public static UTypeApply create(UExpression type, UExpression... typeArguments) {
    return create(type, ImmutableList.copyOf(typeArguments));
  }

  public static UTypeApply create(String type, UExpression... typeArguments) {
    return create(UClassIdent.create(type), typeArguments);
  }

  @Override
  public abstract UExpression getType();

  @Override
  public abstract List<UExpression> getTypeArguments();

  @Override
  @Nullable
  public Unifier unify(JCTree target, @Nullable Unifier unifier) {
    if (unifier != null && target instanceof JCTypeApply) {
      JCTypeApply typeApply = (JCTypeApply) target;
      unifier = getType().unify(typeApply.getType(), unifier);
      return Unifier.unifyList(unifier, getTypeArguments(), typeApply.getTypeArguments());
    }
    return null;
  }

  @Override
  public Kind getKind() {
    return Kind.PARAMETERIZED_TYPE;
  }

  @Override
  public <R, D> R accept(TreeVisitor<R, D> visitor, D data) {
    return visitor.visitParameterizedType(this, data);
  }

  @Override
  public JCTypeApply inline(Inliner inliner) throws CouldNotResolveImportException {
    return inliner.maker().TypeApply(
        getType().inline(inliner),
        inliner.<JCExpression, UExpression>inlineList(getTypeArguments()));
  }
}