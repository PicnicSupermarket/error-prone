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

import java.util.stream.Stream;

/** Example */
public class TransformLambdaTemplateExample {

  public Stream<Integer> test() {
    ImmutableList.of(0).stream().mapToInt(Integer::valueOf);
    ImmutableList.of(1).stream().mapToInt(i -> i * 2);
    ImmutableList.of(2).stream().mapToInt(x -> Integer.valueOf(x));
    ImmutableList.of(3).stream()
        .map(
            y -> {
              if (false) {
                return (Object) y;
              }
              return (int) y;
            });
    ImmutableList.of(4).stream().mapToInt(y -> {
              if (false) {
                return (int) y;
              }
              return (int) y;
            });

    return null;
  }
}
