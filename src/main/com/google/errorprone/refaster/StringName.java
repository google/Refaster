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

import javax.lang.model.element.Name;

/**
 * A simple wrapper to view a {@code String} as a {@code Name}. This is only a substitute for {@code
 * Names.instance(context).fromString(String)} when a {@code Context} is unavailable.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
public abstract class StringName implements Name {
  public static StringName of(String contents) {
    return new AutoValue_StringName(contents);
  }
  
  abstract String contents();

  @Override
  public int length() {
    return contents().length();
  }

  @Override
  public char charAt(int index) {
    return contents().charAt(index);
  }

  @Override
  public CharSequence subSequence(int beginIndex, int endIndex) {
    return contents().subSequence(beginIndex, endIndex);
  }

  @Override
  public String toString() {
    return contents();
  }

  @Override
  public boolean contentEquals(CharSequence cs) {
    return contents().equals(cs.toString());
  }
}
