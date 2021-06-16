/*
 * Copyright 2014 The Error Prone Authors.
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

package com.google.errorprone.refaster.testdata;

import com.google.common.collect.ImmutableList;

/** Example */
public class TransformMethodReferenceTemplateExample {

  public void test() {
    // Positive
    ImmutableList.of(0).stream().map(Integer::valueOf).getClass().getClass();
    ImmutableList.of(1).stream().map(this::bar).getClass().getClass();
    ImmutableList.of(2).stream().map(this::baz).getClass().getClass();
    ImmutableList.of(3).stream().map(this::bax).getClass().getClass();

    // Negative
    ImmutableList.of(4).stream().map(String::valueOf).getClass();
    ImmutableList.of(5).stream().map(this::foo).getClass();
  }

  public Object foo(Integer i) {
    return i;
  }

  public Number baz(Integer i) {
    return i;
  }

  public int bar(int i) {
    return i;
  }

  public Integer bax(Object o) {
    return null;
  }
}
