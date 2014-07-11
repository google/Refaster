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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.TreeInfo;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.lang.model.element.Name;

/**
 * Identifier for a class type.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
public abstract class UClassIdent extends UIdent {
  @VisibleForTesting
  public static UClassIdent create(String qualifiedName) {
    List<String> topLevelPath = new ArrayList<>();
    for (String component : Splitter.on('.').split(qualifiedName)) {
      topLevelPath.add(component);
      if (Character.isUpperCase(component.charAt(0))) {
        break;
      }
    }
    return new AutoValue_UClassIdent(Joiner.on('.').join(topLevelPath), qualifiedName);
  }

  public static UClassIdent create(ClassSymbol sym) {
    return new AutoValue_UClassIdent(
        sym.outermostClass().getQualifiedName().toString(), 
        sym.getQualifiedName().toString());
  }
  
  public abstract String getTopLevelClass();
  
  public abstract String getQualifiedName();

  public ClassSymbol resolve(Inliner inliner) throws CouldNotResolveImportException {
    return inliner.resolveClass(getQualifiedName());
  }

  @Override
  public JCExpression inline(Inliner inliner) throws CouldNotResolveImportException {
    return inliner.importPolicy().classReference(inliner, getTopLevelClass(), getQualifiedName());
  }

  @Override
  @Nullable
  public Unifier unify(JCTree target, @Nullable Unifier unifier) {
    Symbol symbol = TreeInfo.symbol(target);
    return symbol != null && symbol.getQualifiedName().contentEquals(getQualifiedName()) 
        ? unifier : null;
  }

  @Override
  public Name getName() {
    return StringName.of(getQualifiedName());
  }
}
