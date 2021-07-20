package com.google.errorprone.migration;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.MigrationTemplate;

public final class FirstMigrationTemplate {
  private FirstMigrationTemplate() {}

  @MigrationTemplate(value = false, from = String.class, to = Integer.class)
  static final class MigrateStringToInteger<T extends Integer> {
    @BeforeTemplate
    String before(String s) {
      return s;
    }

    @AfterTemplate
    T after(String s) {
      return (T) Integer.valueOf(s);
    }
  }

  @MigrationTemplate(value = true, from = Integer.class, to = String.class)
  static final class MigrateIntegerToString {
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
