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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Objects;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.TypeVar;

import javax.annotation.Nullable;

/**
 * {@link UType} version of {@link TypeVar}.
 *
 * @author Louis Wasserman
 */
public class UTypeVar implements UType {
  // This can't be @AutoValue'd, since the fields are mutable.
  
  public static final class Key extends Bindings.Key<Type> {
    public Key(String name) {
      super(name);
    }
  }
  
  public static UTypeVar create(String name, UType lowerBound, UType upperBound) {
    return new UTypeVar(name, lowerBound, upperBound);
  }

  public static UTypeVar create(String name, UType upperBound) {
    return create(name, UPrimitiveType.NULL, upperBound);
  }

  public static UTypeVar create(String name) {
    return create(name, UClassType.create("java.lang.Object"));
  }

  private final String name;
  private UType lowerBound;
  private UType upperBound;

  private UTypeVar(String name, UType lowerBound, UType upperBound) {
    this.name = checkNotNull(name);
    this.lowerBound = checkNotNull(lowerBound);
    this.upperBound = checkNotNull(upperBound);
  }

  @Override
  @Nullable
  public Unifier unify(Type target, @Nullable Unifier unifier) {
    // This is only called when we're trying to unify overloads, in which case
    // type variables don't matter.
    return !target.isPrimitive() ? unifier : null;
  }
  
  public Key key() {
    return new Key(name);
  }

  public String getName() {
    return name;
  }

  public UType getLowerBound() {
    return lowerBound;
  }

  public UType getUpperBound() {
    return upperBound;
  }

  /**
   * @param lowerBound the lowerBound to set
   */
  public void setLowerBound(UType lowerBound) {
    this.lowerBound = checkNotNull(lowerBound);
  }

  /**
   * @param upperBound the upperBound to set
   */
  public void setUpperBound(UType upperBound) {
    this.upperBound = checkNotNull(upperBound);
  }

  @Override
  public Type inline(Inliner inliner) throws CouldNotResolveImportException {
    return inliner.inlineTypeVar(this);
  }
  
  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    } else if (obj instanceof UTypeVar) {
      UTypeVar typeVar = (UTypeVar) obj;
      return name.equals(typeVar.name)
          && lowerBound.equals(typeVar.lowerBound)
          && upperBound.equals(typeVar.upperBound);
    }
    return false;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .add("name", name)
        .toString();
  }

}
