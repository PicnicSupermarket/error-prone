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

import java.util.concurrent.Callable;

/** Example */
public class TransformThrowsExceptionTemplate<V> {
  @BeforeTemplate
  public void before(@CanTransformToTargetType Callable<V> callable) {
    MyUtil.convert(callable);
  }

  @AfterTemplate
  public void after(Runnable callable) {
    Runnable r = callable;
  }

  public static class MyUtil {
    public static <V> void convert(
        Callable<V> function) {
        try {
          function.call();
        } catch (Exception e) {
          throw new RuntimeException();
        }
    }
  }
}
