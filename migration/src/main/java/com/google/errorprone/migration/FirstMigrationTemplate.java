package com.google.errorprone.migration;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.MigrationTemplate;

public final class FirstMigrationTemplate {
  private FirstMigrationTemplate() {}

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
