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

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.TreeVisitor;
import com.sun.source.tree.TypeParameterTree;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;

import javax.annotation.Nullable;
import javax.lang.model.element.Name;

/**
 * {@code UTree} representation of a {@code MethodTree}.
 * 
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
public abstract class UMethodDecl extends UTree<JCMethodDecl> implements MethodTree {
  public static UMethodDecl create(UModifiers modifiers, String name, UExpression returnType,
      Iterable<UVariableDecl> parameters, Iterable<UExpression> thrown, UBlock body) {
    return new AutoValue_UMethodDecl(modifiers, name, returnType, ImmutableList.copyOf(parameters), 
        ImmutableList.copyOf(thrown), body);
  }
  
  @Override
  public <R, D> R accept(TreeVisitor<R, D> visitor, D data) {
    return visitor.visitMethod(this, data);
  }

  @Override
  public Kind getKind() {
    return Kind.METHOD;
  }

  @Override
  @Nullable
  public Unifier visitMethod(MethodTree decl, @Nullable Unifier unifier) {
    unifier = getName().contentEquals(decl.getName()) ? unifier : null;
    unifier = getReturnType().unify(decl.getReturnType(), unifier);
    unifier = Unifier.unifyList(unifier, getParameters(), decl.getParameters());
    unifier = Unifier.unifyList(unifier, getThrows(), decl.getThrows());
    return getBody().unify(decl.getBody(), unifier);
  }

  @Override
  public JCMethodDecl inline(Inliner inliner) throws CouldNotResolveImportException {
    return inliner.maker().MethodDef(
        getModifiers().inline(inliner),
        inliner.asName(name()),
        getReturnType().inline(inliner),
        List.<JCTypeParameter>nil(),
        List.convert(JCVariableDecl.class, inliner.inlineList(getParameters())),
        inliner.inlineList(getThrows()),
        getBody().inline(inliner),
        null);
  }

  @Override
  public abstract UModifiers getModifiers();

  abstract String name();

  @Override
  public abstract UExpression getReturnType();

  @Override
  public abstract ImmutableList<UVariableDecl> getParameters();

  @Override
  public abstract ImmutableList<UExpression> getThrows();

  @Override
  public abstract UBlock getBody();

  @Override
  public Name getName() {
    return StringName.of(name());
  }

  @Override
  public UTree<?> getDefaultValue() {
    return null;
  }

  @Override
  public ImmutableList<? extends TypeParameterTree> getTypeParameters() {
    return ImmutableList.of();
  }
  
}
