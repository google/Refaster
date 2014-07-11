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

import com.google.common.testing.EqualsTester;
import com.google.common.testing.SerializableTester;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.lang.model.type.TypeKind;

/**
 * Tests for {@link UPrimitiveType}.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@RunWith(JUnit4.class)
public class UPrimitiveTypeTreeTest {
  @Test
  public void equality() {
    new EqualsTester()
        .addEqualityGroup(UPrimitiveTypeTree.create(TypeKind.INT), UPrimitiveTypeTree.INT)
        .addEqualityGroup(UPrimitiveTypeTree.create(TypeKind.LONG), UPrimitiveTypeTree.LONG)
        .addEqualityGroup(UPrimitiveTypeTree.create(TypeKind.DOUBLE), UPrimitiveTypeTree.DOUBLE)
        .addEqualityGroup(UPrimitiveTypeTree.create(TypeKind.FLOAT), UPrimitiveTypeTree.FLOAT)
        .addEqualityGroup(UPrimitiveTypeTree.create(TypeKind.CHAR), UPrimitiveTypeTree.CHAR)
        .addEqualityGroup(UPrimitiveTypeTree.create(TypeKind.VOID), UPrimitiveTypeTree.VOID)
        .addEqualityGroup(UPrimitiveTypeTree.create(TypeKind.NULL), UPrimitiveTypeTree.NULL)
        .addEqualityGroup(UPrimitiveTypeTree.create(TypeKind.BOOLEAN), UPrimitiveTypeTree.BOOLEAN)
        .addEqualityGroup(UPrimitiveTypeTree.create(TypeKind.BYTE), UPrimitiveTypeTree.BYTE)
        .addEqualityGroup(UPrimitiveTypeTree.create(TypeKind.SHORT), UPrimitiveTypeTree.SHORT)
        .testEquals();
  }

  @Test
  public void serialization() {
    SerializableTester.reserializeAndAssert(UPrimitiveTypeTree.INT);
  }
}
