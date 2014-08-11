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

import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.TreeVisitor;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCNewArray;

import java.util.List;

import javax.annotation.Nullable;

/**
 * {@link UTree} version of {@link NewArrayTree}, which represents an array instantiation.
 */
@AutoValue
public abstract class UNewArray extends UExpression implements NewArrayTree {

  public static UNewArray create(UExpression type,
      List<? extends UExpression> dimensions,
      List<? extends UExpression> initializers) {
    return new AutoValue_UNewArray(
        type,
        dimensions != null ? ImmutableList.copyOf(dimensions) : null,
        initializers != null ? ImmutableList.copyOf(initializers) : null);
  }

  @Nullable
  @Override
  public abstract UExpression getType();

  @Nullable
  @Override
  public abstract List<UExpression> getDimensions();

  @Nullable
  @Override
  public abstract List<UExpression> getInitializers();

  @Override
  @Nullable
  public Unifier unify(JCTree target, @Nullable Unifier unifier) {
    if (unifier != null && target instanceof JCNewArray) {
      JCNewArray newArray = (JCNewArray) target;
      unifier = Unifier.unifyNullable(unifier, getType(), newArray.getType());
      unifier = Unifier.unifyList(unifier, getDimensions(), newArray.getDimensions());

      boolean hasRepeated = false;
      List<UExpression> initializers = getInitializers();
      if (initializers != null) {
        for (UExpression initializer : initializers) {
          if (initializer instanceof URepeated) {
            hasRepeated = true;
            break;
          }
        }
      }
      return Unifier.unifyList(unifier, initializers, newArray.getInitializers(), hasRepeated);
    }
    return null;
  }

  @Override
  public Kind getKind() {
    return Kind.NEW_ARRAY;
  }

  @Override
  public <R, D> R accept(TreeVisitor<R, D> visitor, D data) {
    return visitor.visitNewArray(this, data);
  }

  @Override
  public JCNewArray inline(Inliner inliner) throws CouldNotResolveImportException {
    return inliner.maker().NewArray(
        (getType() == null) ? null : getType().inline(inliner),
        (getDimensions() == null) ? null :
            inliner.<JCExpression, UExpression>inlineList(getDimensions()),
        (getInitializers() == null) ? null :
            inliner.<JCExpression, UExpression>inlineList(getInitializers()));
  }
}
