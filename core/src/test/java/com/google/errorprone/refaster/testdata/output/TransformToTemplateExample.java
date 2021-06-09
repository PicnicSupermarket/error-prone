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

/** Example */
public class TransformToTemplateExample {

  public String negative() {
    Number n = (Number) 1;
    return n.toString();
  }

  public String positive() {
    Integer n = (Integer) 1;
    return String.valueOf(n);
  }

// output
//  String testCreateEnumMap() {
//    EnumMap<RoundingMode, Object> roundingModeObjectEnumMap = new EnumMap<>(RoundingMode.class);
//    return roundingModeObjectEnumMap.toString().toLowerCase(Locale.ROOT);
//  }

//  public List<Integer> example() {
//    return
// ImmutableSet.of(1).stream().map(this::test).map(this::test).collect(Collectors.toList());
//  }
//
//  public Integer test(Integer i) {
//    return i + i;
//  }
}
