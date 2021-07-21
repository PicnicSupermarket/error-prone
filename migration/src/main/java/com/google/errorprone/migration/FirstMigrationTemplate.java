package com.google.errorprone.migration;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.MigrationTemplate;

public final class FirstMigrationTemplate {
  private FirstMigrationTemplate() {}

  @MigrationTemplate(value = false)
  static final class MigrateStringToInteger<S extends String> {
    @BeforeTemplate
    S before(S s) {
      return s;
    }

    @AfterTemplate
    Integer after(S s) {
      return Integer.valueOf(s);
    }
  }

  @MigrationTemplate(value = true)
  static final class MigrateIntegerToString<S extends String> {
    @BeforeTemplate
    Integer before(Integer s) {
      return s;
    }

    @AfterTemplate
    S after(Integer s) {
      return (S) String.valueOf(s);
    }
  }
}
