/*
 * Copyright 2021 The Error Prone Authors.
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

package com.google.errorprone.refaster.testdata.template;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.CanTransformToTargetType;

import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

/** Example */
public class TransformWithGenericsTemplate<Z extends Number, A, B,  Y extends Integer> {
  @BeforeTemplate
  public Stream<B> before(Stream<A> stream, @CanTransformToTargetType Function<A, B> function) {
    return stream.map(function);
  }

  @AfterTemplate
  public Stream<B> after(Stream<B> stream, Function<Y, Z> function) {
    return stream;
  }
}