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

import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.TreeVisitor;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCEnhancedForLoop;

import javax.annotation.Nullable;

/**
 * A {@link UTree} representation of a {@link EnhancedForLoopTree}.
 * 
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
public abstract class UEnhancedForLoop implements UStatement, EnhancedForLoopTree {
  public static UEnhancedForLoop create(
      UVariableDecl variable, UExpression elements, UStatement statement) {
    return new AutoValue_UEnhancedForLoop(variable, elements, statement);
  }

  @Override
  public abstract UVariableDecl getVariable();

  @Override
  public abstract UExpression getExpression();

  @Override
  public abstract UStatement getStatement();

  @Override
  public <R, D> R accept(TreeVisitor<R, D> visitor, D data) {
    return visitor.visitEnhancedForLoop(this, data);
  }

  @Override
  public Kind getKind() {
    return Kind.ENHANCED_FOR_LOOP;
  }

  @Override
  public JCEnhancedForLoop inline(Inliner inliner) throws CouldNotResolveImportException {
    return inliner.maker().ForeachLoop(
        getVariable().inline(inliner),
        getExpression().inline(inliner),
        getStatement().inline(inliner));
  }

  @Override
  @Nullable
  public Unifier unify(JCTree target, @Nullable Unifier unifier) {
    if (target instanceof JCEnhancedForLoop) {
      JCEnhancedForLoop loop = (JCEnhancedForLoop) target;
      unifier = getVariable().unify(loop.getVariable(), unifier);
      unifier = getExpression().unify(loop.getExpression(), unifier);
      return getStatement().unify(loop.getStatement(), unifier);
    }
    return null;
  }
}
