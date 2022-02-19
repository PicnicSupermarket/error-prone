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

import com.google.errorprone.matchers.IsNullableMatcher;
import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.Matches;
import com.google.errorprone.refaster.annotation.Placeholder;
import java.util.Optional;

/** Sample template for exercising {@code @SuppressWarnings} handling. */
abstract class NullnessMatcherTemplate<T> {
  @Placeholder(allowsIdentity = true)
  abstract boolean test(T value);

  @BeforeTemplate
  Optional<T> before(@Matches(IsNullableMatcher.class) T input) {
    return test(input) ? Optional.of(input) : Optional.empty();
  }

  @AfterTemplate
  Optional<T> after(T input) {
    return Optional.ofNullable(input).filter(v -> test(v));
  }
}
