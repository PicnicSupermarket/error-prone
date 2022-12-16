/*
 * Copyright 2022 The Error Prone Authors.
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
import com.google.errorprone.refaster.annotation.MayOptionallyUse;
import com.google.errorprone.refaster.annotation.Placeholder;

/**
 * Template to show a {@link LinkageError} related to {@code CaseTree.CaseKind} when running
 * Refaster on Java 17.
 */
abstract class CaseKindLinkageErrorTemplate {
  @Placeholder
  abstract void placeholder(@MayOptionallyUse String s);

  @BeforeTemplate
  void before(String s) {
    if (s.isEmpty()) {
      placeholder(s);
    }
  }

  @AfterTemplate
  void after(String s) {
    if (s.isEmpty()) {
      placeholder(s);
    }
  }
}
