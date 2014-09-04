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

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.TreeVisitor;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCStatement;

import java.util.List;

import javax.annotation.Nullable;

/**
 * {@link UTree} representation of a {@link BlockTree}.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
abstract class UBlock extends UStatement implements BlockTree {
  public static UBlock create(List<UStatement> statements) {
    return new AutoValue_UBlock(ImmutableList.copyOf(statements));
  }

  public static UBlock create(UStatement... statements) {
    return create(ImmutableList.copyOf(statements));
  }

  @Override
  public abstract List<UStatement> getStatements();

  @Override
  @Nullable
  public Unifier visitBlock(BlockTree block, @Nullable Unifier unifier) {
    return Unifier.unifyList(unifier, getStatements(), block.getStatements());
  }

  @Override
  public JCBlock inline(Inliner inliner) throws CouldNotResolveImportException {
    return inliner.maker().Block(0, 
        inliner.<JCStatement, UStatement>inlineList(getStatements()));
  }

  @Override
  public Kind getKind() {
    return Kind.BLOCK;
  }

  @Override
  public <R, D> R accept(TreeVisitor<R, D> visitor, D data) {
    return visitor.visitBlock(this, data);
  }

  @Override
  public boolean isStatic() {
    return false;
  }
}
