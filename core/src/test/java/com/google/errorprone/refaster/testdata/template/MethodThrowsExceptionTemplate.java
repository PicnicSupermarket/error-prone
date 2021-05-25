/*
 * Copyright 2021 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.refaster.testdata.template;

import com.google.errorprone.matchers.MethodThrowsLangExceptionMatcher;
import com.google.errorprone.matchers.MethodThrowsLangIllegalStateException;
import com.google.errorprone.matchers.NullnessMatcher;
import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.Matches;
import com.google.errorprone.refaster.annotation.NotMatches;

import java.util.function.Function;
import java.util.stream.Stream;

public class MethodThrowsExceptionTemplate<I, T extends I, O> {
  @BeforeTemplate
  Stream<O> before(Stream<T> stream, @NotMatches(MethodThrowsLangExceptionMatcher.class) Function<I, O> function) {
    return stream.map(function);
  }

  @AfterTemplate
  Stream<O> after(Stream<T> stream, Function<I, O> function) {
    return stream.map(function).limit(30);
  }
}
