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

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.TreeInfo;

import javax.annotation.Nullable;
import javax.lang.model.element.Name;

/**
 * Identifier representing a static member (field, method, etc.) on a class.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
public abstract class UStaticIdent extends UIdent {
  public static UStaticIdent create(UClassIdent classIdent, String member, UType memberType) {
    return new AutoValue_UStaticIdent(classIdent, member, memberType);
  }

  public static UStaticIdent create(String qualifiedClass, String member, UType memberType) {
    return create(UClassIdent.create(qualifiedClass), member, memberType);
  }

  public static UStaticIdent create(ClassSymbol classSym, String member, UType memberType) {
    return create(UClassIdent.create(classSym), member, memberType);
  }

  abstract UClassIdent classIdent();

  abstract String member();

  abstract UType memberType();

  @Override
  public JCExpression inline(Inliner inliner) throws CouldNotResolveImportException {
    return inliner.importPolicy().staticReference(
        inliner, classIdent().getTopLevelClass(), classIdent().getQualifiedName(), member());
  }

  @Override
  @Nullable
  public Unifier unify(JCTree target, @Nullable Unifier unifier) {
    Symbol symbol = TreeInfo.symbol(target);
    if (symbol != null && symbol.getEnclosingElement() != null
        && symbol.getEnclosingElement().getQualifiedName()
            .contentEquals(classIdent().getQualifiedName())
        && symbol.getSimpleName().contentEquals(member())) {
      return memberType().unify(symbol.asType(), unifier);
    }
    return null;
  }

  @Override
  public Name getName() {
    return StringName.of(member());
  }
}
