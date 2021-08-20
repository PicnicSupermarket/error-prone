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

package com.google.errorprone.migration_resources;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.MigrationTemplate;

public final class StringToIntegerMigrationTemplate {
  private StringToIntegerMigrationTemplate() {}

  static final class AlsoStringToIntegerSecond {
    @MigrationTemplate(value = false)
    static final class MigrateStringToInteger<S extends String, T extends Integer> {
      @BeforeTemplate
      S before(S s) {
        return s;
      }

      @AfterTemplate
      T after(S s) {
        return (T) Integer.valueOf(s);
      }
    }

    @MigrationTemplate(value = true)
    static final class MigrateIntegerToString<S extends String, T extends Integer> {
      @BeforeTemplate
      T before(T s) {
        return s;
      }

      @AfterTemplate
      S after(T s) {
        return (S) String.valueOf(s);
      }
    }
  }

  static final class StringToInteger {
    @MigrationTemplate(value = false)
    static final class MigrateStringToIntegerSecond {
      @BeforeTemplate
      String before(String s) {
        return s;
      }

      @AfterTemplate
      Integer after(String s) {
        return Integer.valueOf(s);
      }
    }

    @MigrationTemplate(value = true)
    static final class MigrateIntegerToStringSecond {
      @BeforeTemplate
      Integer before(Integer s) {
        return s;
      }

      @AfterTemplate
      String after(Integer s) {
        return String.valueOf(s);
      }
    }
  }
}
