/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.spark.unsafe.types;

import org.apache.spark.SparkException;
import org.apache.spark.sql.catalyst.util.CollationAwareUTF8String;
import org.apache.spark.sql.catalyst.util.CollationFactory;
import org.apache.spark.sql.catalyst.util.CollationSupport;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// checkstyle.off: AvoidEscapedUnicodeCharacters
public class CollationSupportSuite {

  /**
   * A list containing some of the supported collations in Spark. Use this list to iterate over
   * all the important collation groups (binary, lowercase, icu) for complete unit test coverage.
   * Note: this list may come in handy when the Spark function result is the same regardless of
   * the specified collations (as often seen in some pass-through Spark expressions).
   */
  private final String[] testSupportedCollations =
    {"UTF8_BINARY", "UTF8_LCASE", "UNICODE", "UNICODE_CI"};

  /**
   * Collation-aware UTF8String comparison.
   */

  private void assertStringCompare(String s1, String s2, String collationName, int expected)
      throws SparkException {
    UTF8String l = UTF8String.fromString(s1);
    UTF8String r = UTF8String.fromString(s2);
    int compare = CollationFactory.fetchCollation(collationName).comparator.compare(l, r);
    assertEquals(Integer.signum(expected), Integer.signum(compare));
  }

  @Test
  public void testCompare() throws SparkException {
    for (String collationName: testSupportedCollations) {
      // Edge cases
      assertStringCompare("", "", collationName, 0);
      assertStringCompare("a", "", collationName, 1);
      assertStringCompare("", "a", collationName, -1);
      // Basic tests
      assertStringCompare("a", "a", collationName, 0);
      assertStringCompare("a", "b", collationName, -1);
      assertStringCompare("b", "a", collationName, 1);
      assertStringCompare("A", "A", collationName, 0);
      assertStringCompare("A", "B", collationName, -1);
      assertStringCompare("B", "A", collationName, 1);
      assertStringCompare("aa", "a", collationName, 1);
      assertStringCompare("b", "bb", collationName, -1);
      assertStringCompare("abc", "a", collationName, 1);
      assertStringCompare("abc", "b", collationName, -1);
      assertStringCompare("abc", "ab", collationName, 1);
      assertStringCompare("abc", "abc", collationName, 0);
      // ASCII strings
      assertStringCompare("aaaa", "aaa", collationName, 1);
      assertStringCompare("hello", "world", collationName, -1);
      assertStringCompare("Spark", "Spark", collationName, 0);
      // Non-ASCII strings
      assertStringCompare("ü", "ü", collationName, 0);
      assertStringCompare("ü", "", collationName, 1);
      assertStringCompare("", "ü", collationName, -1);
      assertStringCompare("äü", "äü", collationName, 0);
      assertStringCompare("äxx", "äx", collationName, 1);
      assertStringCompare("a", "ä", collationName, -1);
    }
    // Non-ASCII strings
    assertStringCompare("äü", "bü", "UTF8_BINARY", 1);
    assertStringCompare("bxx", "bü", "UTF8_BINARY", -1);
    assertStringCompare("äü", "bü", "UTF8_LCASE", 1);
    assertStringCompare("bxx", "bü", "UTF8_LCASE", -1);
    assertStringCompare("äü", "bü", "UNICODE", -1);
    assertStringCompare("bxx", "bü", "UNICODE", 1);
    assertStringCompare("äü", "bü", "UNICODE_CI", -1);
    assertStringCompare("bxx", "bü", "UNICODE_CI", 1);
    // Case variation
    assertStringCompare("AbCd", "aBcD", "UTF8_BINARY", -1);
    assertStringCompare("ABCD", "abcd", "UTF8_LCASE", 0);
    assertStringCompare("AbcD", "aBCd", "UNICODE", 1);
    assertStringCompare("abcd", "ABCD", "UNICODE_CI", 0);
    // Accent variation
    assertStringCompare("aBćD", "ABĆD", "UTF8_BINARY", 1);
    assertStringCompare("AbCδ", "ABCΔ", "UTF8_LCASE", 0);
    assertStringCompare("äBCd", "ÄBCD", "UNICODE", -1);
    assertStringCompare("Ab́cD", "AB́CD", "UNICODE_CI", 0);
    // Case-variable character length
    assertStringCompare("i\u0307", "İ", "UTF8_BINARY", -1);
    assertStringCompare("İ", "i\u0307", "UTF8_BINARY", 1);
    assertStringCompare("i\u0307", "İ", "UTF8_LCASE", 0);
    assertStringCompare("İ", "i\u0307", "UTF8_LCASE", 0);
    assertStringCompare("i\u0307", "İ", "UNICODE", -1);
    assertStringCompare("İ", "i\u0307", "UNICODE", 1);
    assertStringCompare("i\u0307", "İ", "UNICODE_CI", 0);
    assertStringCompare("İ", "i\u0307", "UNICODE_CI", 0);
    assertStringCompare("i\u0307İ", "i\u0307İ", "UTF8_LCASE", 0);
    assertStringCompare("i\u0307İ", "İi\u0307", "UTF8_LCASE", 0);
    assertStringCompare("İi\u0307", "i\u0307İ", "UTF8_LCASE", 0);
    assertStringCompare("İi\u0307", "İi\u0307", "UTF8_LCASE", 0);
    assertStringCompare("i\u0307İ", "i\u0307İ", "UNICODE_CI", 0);
    assertStringCompare("i\u0307İ", "İi\u0307", "UNICODE_CI", 0);
    assertStringCompare("İi\u0307", "i\u0307İ", "UNICODE_CI", 0);
    assertStringCompare("İi\u0307", "İi\u0307", "UNICODE_CI", 0);
    // Conditional case mapping
    assertStringCompare("ς", "σ", "UTF8_BINARY", -1);
    assertStringCompare("ς", "Σ", "UTF8_BINARY", 1);
    assertStringCompare("σ", "Σ", "UTF8_BINARY", 1);
    assertStringCompare("ς", "σ", "UTF8_LCASE", 0);
    assertStringCompare("ς", "Σ", "UTF8_LCASE", 0);
    assertStringCompare("σ", "Σ", "UTF8_LCASE", 0);
    assertStringCompare("ς", "σ", "UNICODE", 1);
    assertStringCompare("ς", "Σ", "UNICODE", 1);
    assertStringCompare("σ", "Σ", "UNICODE", -1);
    assertStringCompare("ς", "σ", "UNICODE_CI", 0);
    assertStringCompare("ς", "Σ", "UNICODE_CI", 0);
    assertStringCompare("σ", "Σ", "UNICODE_CI", 0);
    // Maximum code point.
    int maxCodePoint = Character.MAX_CODE_POINT;
    String maxCodePointStr = new String(Character.toChars(maxCodePoint));
    for (int i = 0; i < maxCodePoint && Character.isValidCodePoint(i); ++i) {
      assertStringCompare(new String(Character.toChars(i)), maxCodePointStr, "UTF8_BINARY", -1);
      assertStringCompare(new String(Character.toChars(i)), maxCodePointStr, "UTF8_LCASE", -1);
    }
    // Minimum code point.
    int minCodePoint = Character.MIN_CODE_POINT;
    String minCodePointStr = new String(Character.toChars(minCodePoint));
    for (int i = minCodePoint + 1; i <= maxCodePoint && Character.isValidCodePoint(i); ++i) {
      assertStringCompare(new String(Character.toChars(i)), minCodePointStr, "UTF8_BINARY", 1);
      assertStringCompare(new String(Character.toChars(i)), minCodePointStr, "UTF8_LCASE", 1);
    }
  }

  private void assertLowerCaseCodePoints(UTF8String target, UTF8String expected,
      Boolean useCodePoints) {
    if (useCodePoints) {
      assertEquals(expected, CollationAwareUTF8String.lowerCaseCodePoints(target));
    } else {
      assertEquals(expected, target.toLowerCase());
    }
  }

  @Test
  public void testLowerCaseCodePoints() {
    // Edge cases
    assertLowerCaseCodePoints(UTF8String.fromString(""), UTF8String.fromString(""), false);
    assertLowerCaseCodePoints(UTF8String.fromString(""), UTF8String.fromString(""), true);
    // Basic tests
    assertLowerCaseCodePoints(UTF8String.fromString("abcd"), UTF8String.fromString("abcd"), false);
    assertLowerCaseCodePoints(UTF8String.fromString("AbCd"), UTF8String.fromString("abcd"), false);
    assertLowerCaseCodePoints(UTF8String.fromString("abcd"), UTF8String.fromString("abcd"), true);
    assertLowerCaseCodePoints(UTF8String.fromString("aBcD"), UTF8String.fromString("abcd"), true);
    // Accent variation
    assertLowerCaseCodePoints(UTF8String.fromString("AbĆd"), UTF8String.fromString("abćd"), false);
    assertLowerCaseCodePoints(UTF8String.fromString("aBcΔ"), UTF8String.fromString("abcδ"), true);
    // Case-variable character length
    assertLowerCaseCodePoints(
      UTF8String.fromString("İoDiNe"), UTF8String.fromString("i̇odine"), false);
    assertLowerCaseCodePoints(
      UTF8String.fromString("Abi̇o12"), UTF8String.fromString("abi̇o12"), false);
    assertLowerCaseCodePoints(
      UTF8String.fromString("İodInE"), UTF8String.fromString("i̇odine"), true);
    assertLowerCaseCodePoints(
      UTF8String.fromString("aBi̇o12"), UTF8String.fromString("abi̇o12"), true);
    // Conditional case mapping
    assertLowerCaseCodePoints(
      UTF8String.fromString("ΘΑΛΑΣΣΙΝΟΣ"), UTF8String.fromString("θαλασσινος"), false);
    assertLowerCaseCodePoints(
      UTF8String.fromString("ΘΑΛΑΣΣΙΝΟΣ"), UTF8String.fromString("θαλασσινοσ"), true);
    // Surrogate pairs are treated as invalid UTF8 sequences
    assertLowerCaseCodePoints(UTF8String.fromBytes(new byte[]
      {(byte) 0xED, (byte) 0xA0, (byte) 0x80, (byte) 0xED, (byte) 0xB0, (byte) 0x80}),
      UTF8String.fromString("\uFFFD\uFFFD"), false);
    assertLowerCaseCodePoints(UTF8String.fromBytes(new byte[]
      {(byte) 0xED, (byte) 0xA0, (byte) 0x80, (byte) 0xED, (byte) 0xB0, (byte) 0x80}),
      UTF8String.fromString("\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD"), true); // != Java toLowerCase
  }

  /**
   * Collation-aware string expressions.
   */

  private void assertContains(String pattern, String target, String collationName, boolean expected)
          throws SparkException {
    UTF8String l = UTF8String.fromString(pattern);
    UTF8String r = UTF8String.fromString(target);
    int collationId = CollationFactory.collationNameToId(collationName);
    assertEquals(expected, CollationSupport.Contains.exec(l, r, collationId));
  }

  @Test
  public void testContains() throws SparkException {
    // Edge cases
    assertContains("", "", "UTF8_BINARY", true);
    assertContains("c", "", "UTF8_BINARY", true);
    assertContains("", "c", "UTF8_BINARY", false);
    assertContains("", "", "UNICODE", true);
    assertContains("c", "", "UNICODE", true);
    assertContains("", "c", "UNICODE", false);
    assertContains("", "", "UTF8_LCASE", true);
    assertContains("c", "", "UTF8_LCASE", true);
    assertContains("", "c", "UTF8_LCASE", false);
    assertContains("", "", "UNICODE_CI", true);
    assertContains("c", "", "UNICODE_CI", true);
    assertContains("", "c", "UNICODE_CI", false);
    // Basic tests
    assertContains("abcde", "bcd", "UTF8_BINARY", true);
    assertContains("abcde", "bde", "UTF8_BINARY", false);
    assertContains("abcde", "fgh", "UTF8_BINARY", false);
    assertContains("abcde", "abcde", "UNICODE", true);
    assertContains("abcde", "aBcDe", "UNICODE", false);
    assertContains("abcde", "fghij", "UNICODE", false);
    assertContains("abcde", "C", "UTF8_LCASE", true);
    assertContains("abcde", "AbCdE", "UTF8_LCASE", true);
    assertContains("abcde", "X", "UTF8_LCASE", false);
    assertContains("abcde", "c", "UNICODE_CI", true);
    assertContains("abcde", "bCD", "UNICODE_CI", true);
    assertContains("abcde", "123", "UNICODE_CI", false);
    // Case variation
    assertContains("aBcDe", "bcd", "UTF8_BINARY", false);
    assertContains("aBcDe", "BcD", "UTF8_BINARY", true);
    assertContains("aBcDe", "abcde", "UNICODE", false);
    assertContains("aBcDe", "aBcDe", "UNICODE", true);
    assertContains("aBcDe", "bcd", "UTF8_LCASE", true);
    assertContains("aBcDe", "BCD", "UTF8_LCASE", true);
    assertContains("aBcDe", "abcde", "UNICODE_CI", true);
    assertContains("aBcDe", "AbCdE", "UNICODE_CI", true);
    // Accent variation
    assertContains("aBcDe", "bćd", "UTF8_BINARY", false);
    assertContains("aBcDe", "BćD", "UTF8_BINARY", false);
    assertContains("aBcDe", "abćde", "UNICODE", false);
    assertContains("aBcDe", "aBćDe", "UNICODE", false);
    assertContains("aBcDe", "bćd", "UTF8_LCASE", false);
    assertContains("aBcDe", "BĆD", "UTF8_LCASE", false);
    assertContains("aBcDe", "abćde", "UNICODE_CI", false);
    assertContains("aBcDe", "AbĆdE", "UNICODE_CI", false);
    // Variable byte length characters
    assertContains("ab世De", "b世D", "UTF8_BINARY", true);
    assertContains("ab世De", "B世d", "UTF8_BINARY", false);
    assertContains("äbćδe", "bćδ", "UTF8_BINARY", true);
    assertContains("äbćδe", "BcΔ", "UTF8_BINARY", false);
    assertContains("ab世De", "ab世De", "UNICODE", true);
    assertContains("ab世De", "AB世dE", "UNICODE", false);
    assertContains("äbćδe", "äbćδe", "UNICODE", true);
    assertContains("äbćδe", "ÄBcΔÉ", "UNICODE", false);
    assertContains("ab世De", "b世D", "UTF8_LCASE", true);
    assertContains("ab世De", "B世d", "UTF8_LCASE", true);
    assertContains("äbćδe", "bćδ", "UTF8_LCASE", true);
    assertContains("äbćδe", "BcΔ", "UTF8_LCASE", false);
    assertContains("ab世De", "ab世De", "UNICODE_CI", true);
    assertContains("ab世De", "AB世dE", "UNICODE_CI", true);
    assertContains("äbćδe", "ÄbćδE", "UNICODE_CI", true);
    assertContains("äbćδe", "ÄBcΔÉ", "UNICODE_CI", false);
    // Characters with the same binary lowercase representation
    assertContains("The Kelvin.", "Kelvin", "UTF8_LCASE", true);
    assertContains("The Kelvin.", "Kelvin", "UTF8_LCASE", true);
    assertContains("The KKelvin.", "KKelvin", "UTF8_LCASE", true);
    assertContains("2 Kelvin.", "2 Kelvin", "UTF8_LCASE", true);
    assertContains("2 Kelvin.", "2 Kelvin", "UTF8_LCASE", true);
    assertContains("The KKelvin.", "KKelvin,", "UTF8_LCASE", false);
    // Case-variable character length
    assertContains("i̇", "i", "UNICODE_CI", false);
    assertContains("i̇", "\u0307", "UNICODE_CI", false);
    assertContains("i̇", "İ", "UNICODE_CI", true);
    assertContains("İ", "i", "UNICODE_CI", false);
    assertContains("adi̇os", "io", "UNICODE_CI", false);
    assertContains("adi̇os", "Io", "UNICODE_CI", false);
    assertContains("adi̇os", "i̇o", "UNICODE_CI", true);
    assertContains("adi̇os", "İo", "UNICODE_CI", true);
    assertContains("adİos", "io", "UNICODE_CI", false);
    assertContains("adİos", "Io", "UNICODE_CI", false);
    assertContains("adİos", "i̇o", "UNICODE_CI", true);
    assertContains("adİos", "İo", "UNICODE_CI", true);
    assertContains("i̇", "i", "UTF8_LCASE", true); // != UNICODE_CI
    assertContains("İ", "\u0307", "UTF8_LCASE", false);
    assertContains("İ", "i", "UTF8_LCASE", false);
    assertContains("i̇", "\u0307", "UTF8_LCASE", true); // != UNICODE_CI
    assertContains("i̇", "İ", "UTF8_LCASE", true);
    assertContains("İ", "i", "UTF8_LCASE", false);
    assertContains("adi̇os", "io", "UTF8_LCASE", false);
    assertContains("adi̇os", "Io", "UTF8_LCASE", false);
    assertContains("adi̇os", "i̇o", "UTF8_LCASE", true);
    assertContains("adi̇os", "İo", "UTF8_LCASE", true);
    assertContains("adİos", "io", "UTF8_LCASE", false);
    assertContains("adİos", "Io", "UTF8_LCASE", false);
    assertContains("adİos", "i̇o", "UTF8_LCASE", true);
    assertContains("adİos", "İo", "UTF8_LCASE", true);
    // Greek sigmas.
    assertContains("σ", "σ", "UTF8_BINARY", true);
    assertContains("σ", "ς", "UTF8_BINARY", false);
    assertContains("σ", "Σ", "UTF8_BINARY", false);
    assertContains("ς", "σ", "UTF8_BINARY", false);
    assertContains("ς", "ς", "UTF8_BINARY", true);
    assertContains("ς", "Σ", "UTF8_BINARY", false);
    assertContains("Σ", "σ", "UTF8_BINARY", false);
    assertContains("Σ", "ς", "UTF8_BINARY", false);
    assertContains("Σ", "Σ", "UTF8_BINARY", true);
    assertContains("σ", "σ", "UTF8_LCASE", true);
    assertContains("σ", "ς", "UTF8_LCASE", true);
    assertContains("σ", "Σ", "UTF8_LCASE", true);
    assertContains("ς", "σ", "UTF8_LCASE", true);
    assertContains("ς", "ς", "UTF8_LCASE", true);
    assertContains("ς", "Σ", "UTF8_LCASE", true);
    assertContains("Σ", "σ", "UTF8_LCASE", true);
    assertContains("Σ", "ς", "UTF8_LCASE", true);
    assertContains("Σ", "Σ", "UTF8_LCASE", true);
    assertContains("σ", "σ", "UNICODE", true);
    assertContains("σ", "ς", "UNICODE", false);
    assertContains("σ", "Σ", "UNICODE", false);
    assertContains("ς", "σ", "UNICODE", false);
    assertContains("ς", "ς", "UNICODE", true);
    assertContains("ς", "Σ", "UNICODE", false);
    assertContains("Σ", "σ", "UNICODE", false);
    assertContains("Σ", "ς", "UNICODE", false);
    assertContains("Σ", "Σ", "UNICODE", true);
    assertContains("σ", "σ", "UNICODE_CI", true);
    assertContains("σ", "ς", "UNICODE_CI", true);
    assertContains("σ", "Σ", "UNICODE_CI", true);
    assertContains("ς", "σ", "UNICODE_CI", true);
    assertContains("ς", "ς", "UNICODE_CI", true);
    assertContains("ς", "Σ", "UNICODE_CI", true);
    assertContains("Σ", "σ", "UNICODE_CI", true);
    assertContains("Σ", "ς", "UNICODE_CI", true);
    assertContains("Σ", "Σ", "UNICODE_CI", true);
  }

  private void assertStartsWith(
          String pattern, String prefix, String collationName, boolean expected)
          throws SparkException {
    UTF8String l = UTF8String.fromString(pattern);
    UTF8String r = UTF8String.fromString(prefix);
    int collationId = CollationFactory.collationNameToId(collationName);
    assertEquals(expected, CollationSupport.StartsWith.exec(l, r, collationId));
  }

  @Test
  public void testStartsWith() throws SparkException {
    // Edge cases
    assertStartsWith("", "", "UTF8_BINARY", true);
    assertStartsWith("c", "", "UTF8_BINARY", true);
    assertStartsWith("", "c", "UTF8_BINARY", false);
    assertStartsWith("", "", "UNICODE", true);
    assertStartsWith("c", "", "UNICODE", true);
    assertStartsWith("", "c", "UNICODE", false);
    assertStartsWith("", "", "UTF8_LCASE", true);
    assertStartsWith("c", "", "UTF8_LCASE", true);
    assertStartsWith("", "c", "UTF8_LCASE", false);
    assertStartsWith("", "", "UNICODE_CI", true);
    assertStartsWith("c", "", "UNICODE_CI", true);
    assertStartsWith("", "c", "UNICODE_CI", false);
    // Basic tests
    assertStartsWith("abcde", "abc", "UTF8_BINARY", true);
    assertStartsWith("abcde", "abd", "UTF8_BINARY", false);
    assertStartsWith("abcde", "fgh", "UTF8_BINARY", false);
    assertStartsWith("abcde", "abcde", "UNICODE", true);
    assertStartsWith("abcde", "aBcDe", "UNICODE", false);
    assertStartsWith("abcde", "fghij", "UNICODE", false);
    assertStartsWith("abcde", "A", "UTF8_LCASE", true);
    assertStartsWith("abcde", "AbCdE", "UTF8_LCASE", true);
    assertStartsWith("abcde", "X", "UTF8_LCASE", false);
    assertStartsWith("abcde", "a", "UNICODE_CI", true);
    assertStartsWith("abcde", "aBC", "UNICODE_CI", true);
    assertStartsWith("abcde", "bcd", "UNICODE_CI", false);
    assertStartsWith("abcde", "123", "UNICODE_CI", false);
    // Case variation
    assertStartsWith("aBcDe", "abc", "UTF8_BINARY", false);
    assertStartsWith("aBcDe", "aBc", "UTF8_BINARY", true);
    assertStartsWith("aBcDe", "abcde", "UNICODE", false);
    assertStartsWith("aBcDe", "aBcDe", "UNICODE", true);
    assertStartsWith("aBcDe", "abc", "UTF8_LCASE", true);
    assertStartsWith("aBcDe", "ABC", "UTF8_LCASE", true);
    assertStartsWith("aBcDe", "abcde", "UNICODE_CI", true);
    assertStartsWith("aBcDe", "AbCdE", "UNICODE_CI", true);
    // Accent variation
    assertStartsWith("aBcDe", "abć", "UTF8_BINARY", false);
    assertStartsWith("aBcDe", "aBć", "UTF8_BINARY", false);
    assertStartsWith("aBcDe", "abćde", "UNICODE", false);
    assertStartsWith("aBcDe", "aBćDe", "UNICODE", false);
    assertStartsWith("aBcDe", "abć", "UTF8_LCASE", false);
    assertStartsWith("aBcDe", "ABĆ", "UTF8_LCASE", false);
    assertStartsWith("aBcDe", "abćde", "UNICODE_CI", false);
    assertStartsWith("aBcDe", "AbĆdE", "UNICODE_CI", false);
    // Variable byte length characters
    assertStartsWith("ab世De", "ab世", "UTF8_BINARY", true);
    assertStartsWith("ab世De", "aB世", "UTF8_BINARY", false);
    assertStartsWith("äbćδe", "äbć", "UTF8_BINARY", true);
    assertStartsWith("äbćδe", "äBc", "UTF8_BINARY", false);
    assertStartsWith("ab世De", "ab世De", "UNICODE", true);
    assertStartsWith("ab世De", "AB世dE", "UNICODE", false);
    assertStartsWith("äbćδe", "äbćδe", "UNICODE", true);
    assertStartsWith("äbćδe", "ÄBcΔÉ", "UNICODE", false);
    assertStartsWith("ab世De", "ab世", "UTF8_LCASE", true);
    assertStartsWith("ab世De", "aB世", "UTF8_LCASE", true);
    assertStartsWith("äbćδe", "äbć", "UTF8_LCASE", true);
    assertStartsWith("äbćδe", "äBc", "UTF8_LCASE", false);
    assertStartsWith("ab世De", "ab世De", "UNICODE_CI", true);
    assertStartsWith("ab世De", "AB世dE", "UNICODE_CI", true);
    assertStartsWith("äbćδe", "ÄbćδE", "UNICODE_CI", true);
    assertStartsWith("äbćδe", "ÄBcΔÉ", "UNICODE_CI", false);
    // Characters with the same binary lowercase representation
    assertStartsWith("Kelvin.", "Kelvin", "UTF8_LCASE", true);
    assertStartsWith("Kelvin.", "Kelvin", "UTF8_LCASE", true);
    assertStartsWith("KKelvin.", "KKelvin", "UTF8_LCASE", true);
    assertStartsWith("2 Kelvin.", "2 Kelvin", "UTF8_LCASE", true);
    assertStartsWith("2 Kelvin.", "2 Kelvin", "UTF8_LCASE", true);
    assertStartsWith("KKelvin.", "KKelvin,", "UTF8_LCASE", false);
    // Case-variable character length
    assertStartsWith("i̇", "i", "UNICODE_CI", false);
    assertStartsWith("i̇", "İ", "UNICODE_CI", true);
    assertStartsWith("İ", "i", "UNICODE_CI", false);
    assertStartsWith("İİİ", "i̇i̇", "UNICODE_CI", true);
    assertStartsWith("İİİ", "i̇i", "UNICODE_CI", false);
    assertStartsWith("İi̇İ", "i̇İ", "UNICODE_CI", true);
    assertStartsWith("i̇İi̇i̇", "İi̇İi", "UNICODE_CI", false);
    assertStartsWith("i̇onic", "io", "UNICODE_CI", false);
    assertStartsWith("i̇onic", "Io", "UNICODE_CI", false);
    assertStartsWith("i̇onic", "i̇o", "UNICODE_CI", true);
    assertStartsWith("i̇onic", "İo", "UNICODE_CI", true);
    assertStartsWith("İonic", "io", "UNICODE_CI", false);
    assertStartsWith("İonic", "Io", "UNICODE_CI", false);
    assertStartsWith("İonic", "i̇o", "UNICODE_CI", true);
    assertStartsWith("İonic", "İo", "UNICODE_CI", true);
    assertStartsWith("i̇", "i", "UTF8_LCASE", true); // != UNICODE_CI
    assertStartsWith("i̇", "İ", "UTF8_LCASE", true);
    assertStartsWith("İ", "i", "UTF8_LCASE", false);
    assertStartsWith("İİİ", "i̇i̇", "UTF8_LCASE", true);
    assertStartsWith("İİİ", "i̇i", "UTF8_LCASE", false);
    assertStartsWith("İi̇İ", "i̇İ", "UTF8_LCASE", true);
    assertStartsWith("i̇İi̇i̇", "İi̇İi", "UTF8_LCASE", true); // != UNICODE_CI
    assertStartsWith("i̇onic", "io", "UTF8_LCASE", false);
    assertStartsWith("i̇onic", "Io", "UTF8_LCASE", false);
    assertStartsWith("i̇onic", "i̇o", "UTF8_LCASE", true);
    assertStartsWith("i̇onic", "İo", "UTF8_LCASE", true);
    assertStartsWith("İonic", "io", "UTF8_LCASE", false);
    assertStartsWith("İonic", "Io", "UTF8_LCASE", false);
    assertStartsWith("İonic", "i̇o", "UTF8_LCASE", true);
    assertStartsWith("İonic", "İo", "UTF8_LCASE", true);
  }

  private void assertEndsWith(String pattern, String suffix, String collationName, boolean expected)
          throws SparkException {
    UTF8String l = UTF8String.fromString(pattern);
    UTF8String r = UTF8String.fromString(suffix);
    int collationId = CollationFactory.collationNameToId(collationName);
    assertEquals(expected, CollationSupport.EndsWith.exec(l, r, collationId));
  }

  @Test
  public void testEndsWith() throws SparkException {
    // Edge cases
    assertEndsWith("", "", "UTF8_BINARY", true);
    assertEndsWith("c", "", "UTF8_BINARY", true);
    assertEndsWith("", "c", "UTF8_BINARY", false);
    assertEndsWith("", "", "UNICODE", true);
    assertEndsWith("c", "", "UNICODE", true);
    assertEndsWith("", "c", "UNICODE", false);
    assertEndsWith("", "", "UTF8_LCASE", true);
    assertEndsWith("c", "", "UTF8_LCASE", true);
    assertEndsWith("", "c", "UTF8_LCASE", false);
    assertEndsWith("", "", "UNICODE_CI", true);
    assertEndsWith("c", "", "UNICODE_CI", true);
    assertEndsWith("", "c", "UNICODE_CI", false);
    // Basic tests
    assertEndsWith("abcde", "cde", "UTF8_BINARY", true);
    assertEndsWith("abcde", "bde", "UTF8_BINARY", false);
    assertEndsWith("abcde", "fgh", "UTF8_BINARY", false);
    assertEndsWith("abcde", "abcde", "UNICODE", true);
    assertEndsWith("abcde", "aBcDe", "UNICODE", false);
    assertEndsWith("abcde", "fghij", "UNICODE", false);
    assertEndsWith("abcde", "E", "UTF8_LCASE", true);
    assertEndsWith("abcde", "AbCdE", "UTF8_LCASE", true);
    assertEndsWith("abcde", "X", "UTF8_LCASE", false);
    assertEndsWith("abcde", "e", "UNICODE_CI", true);
    assertEndsWith("abcde", "CDe", "UNICODE_CI", true);
    assertEndsWith("abcde", "bcd", "UNICODE_CI", false);
    assertEndsWith("abcde", "123", "UNICODE_CI", false);
    // Case variation
    assertEndsWith("aBcDe", "cde", "UTF8_BINARY", false);
    assertEndsWith("aBcDe", "cDe", "UTF8_BINARY", true);
    assertEndsWith("aBcDe", "abcde", "UNICODE", false);
    assertEndsWith("aBcDe", "aBcDe", "UNICODE", true);
    assertEndsWith("aBcDe", "cde", "UTF8_LCASE", true);
    assertEndsWith("aBcDe", "CDE", "UTF8_LCASE", true);
    assertEndsWith("aBcDe", "abcde", "UNICODE_CI", true);
    assertEndsWith("aBcDe", "AbCdE", "UNICODE_CI", true);
    // Accent variation
    assertEndsWith("aBcDe", "ćde", "UTF8_BINARY", false);
    assertEndsWith("aBcDe", "ćDe", "UTF8_BINARY", false);
    assertEndsWith("aBcDe", "abćde", "UNICODE", false);
    assertEndsWith("aBcDe", "aBćDe", "UNICODE", false);
    assertEndsWith("aBcDe", "ćde", "UTF8_LCASE", false);
    assertEndsWith("aBcDe", "ĆDE", "UTF8_LCASE", false);
    assertEndsWith("aBcDe", "abćde", "UNICODE_CI", false);
    assertEndsWith("aBcDe", "AbĆdE", "UNICODE_CI", false);
    // Variable byte length characters
    assertEndsWith("ab世De", "世De", "UTF8_BINARY", true);
    assertEndsWith("ab世De", "世dE", "UTF8_BINARY", false);
    assertEndsWith("äbćδe", "ćδe", "UTF8_BINARY", true);
    assertEndsWith("äbćδe", "cΔé", "UTF8_BINARY", false);
    assertEndsWith("ab世De", "ab世De", "UNICODE", true);
    assertEndsWith("ab世De", "AB世dE", "UNICODE", false);
    assertEndsWith("äbćδe", "äbćδe", "UNICODE", true);
    assertEndsWith("äbćδe", "ÄBcΔÉ", "UNICODE", false);
    assertEndsWith("ab世De", "世De", "UTF8_LCASE", true);
    assertEndsWith("ab世De", "世dE", "UTF8_LCASE", true);
    assertEndsWith("äbćδe", "ćδe", "UTF8_LCASE", true);
    assertEndsWith("äbćδe", "cδE", "UTF8_LCASE", false);
    assertEndsWith("ab世De", "ab世De", "UNICODE_CI", true);
    assertEndsWith("ab世De", "AB世dE", "UNICODE_CI", true);
    assertEndsWith("äbćδe", "ÄbćδE", "UNICODE_CI", true);
    assertEndsWith("äbćδe", "ÄBcΔÉ", "UNICODE_CI", false);
    // Characters with the same binary lowercase representation
    assertEndsWith("The Kelvin", "Kelvin", "UTF8_LCASE", true);
    assertEndsWith("The Kelvin", "Kelvin", "UTF8_LCASE", true);
    assertEndsWith("The KKelvin", "KKelvin", "UTF8_LCASE", true);
    assertEndsWith("The 2 Kelvin", "2 Kelvin", "UTF8_LCASE", true);
    assertEndsWith("The 2 Kelvin", "2 Kelvin", "UTF8_LCASE", true);
    assertEndsWith("The KKelvin", "KKelvin,", "UTF8_LCASE", false);
    // Case-variable character length
    assertEndsWith("i̇", "\u0307", "UNICODE_CI", false);
    assertEndsWith("i̇", "İ", "UNICODE_CI", true);
    assertEndsWith("İ", "i", "UNICODE_CI", false);
    assertEndsWith("İİİ", "i̇i̇", "UNICODE_CI", true);
    assertEndsWith("İİİ", "ii̇", "UNICODE_CI", false);
    assertEndsWith("İi̇İ", "İi̇", "UNICODE_CI", true);
    assertEndsWith("i̇İi̇i̇", "\u0307İi̇İ", "UNICODE_CI", false);
    assertEndsWith("the i̇o", "io", "UNICODE_CI", false);
    assertEndsWith("the i̇o", "Io", "UNICODE_CI", false);
    assertEndsWith("the i̇o", "i̇o", "UNICODE_CI", true);
    assertEndsWith("the i̇o", "İo", "UNICODE_CI", true);
    assertEndsWith("the İo", "io", "UNICODE_CI", false);
    assertEndsWith("the İo", "Io", "UNICODE_CI", false);
    assertEndsWith("the İo", "i̇o", "UNICODE_CI", true);
    assertEndsWith("the İo", "İo", "UNICODE_CI", true);
    assertEndsWith("i̇", "\u0307", "UTF8_LCASE", true); // != UNICODE_CI
    assertEndsWith("i̇", "İ", "UTF8_LCASE", true);
    assertEndsWith("İ", "\u0307", "UTF8_LCASE", false);
    assertEndsWith("İİİ", "i̇i̇", "UTF8_LCASE", true);
    assertEndsWith("İİİ", "ii̇", "UTF8_LCASE", false);
    assertEndsWith("İi̇İ", "İi̇", "UTF8_LCASE", true);
    assertEndsWith("i̇İi̇i̇", "\u0307İi̇İ", "UTF8_LCASE", true); // != UNICODE_CI
    assertEndsWith("i̇İi̇i̇", "\u0307İİ", "UTF8_LCASE", false);
    assertEndsWith("the i̇o", "io", "UTF8_LCASE", false);
    assertEndsWith("the i̇o", "Io", "UTF8_LCASE", false);
    assertEndsWith("the i̇o", "i̇o", "UTF8_LCASE", true);
    assertEndsWith("the i̇o", "İo", "UTF8_LCASE", true);
    assertEndsWith("the İo", "io", "UTF8_LCASE", false);
    assertEndsWith("the İo", "Io", "UTF8_LCASE", false);
    assertEndsWith("the İo", "i̇o", "UTF8_LCASE", true);
    assertEndsWith("the İo", "İo", "UTF8_LCASE", true);
  }

  /**
   * Verify the behaviour of the `StringSplitSQL` collation support class.
   */

  private void assertStringSplitSQL(String str, String delimiter, String collationName,
      UTF8String[] expected) throws SparkException {
    UTF8String s = UTF8String.fromString(str);
    UTF8String d = UTF8String.fromString(delimiter);
    int collationId = CollationFactory.collationNameToId(collationName);
    UTF8String[] result = CollationSupport.StringSplitSQL.exec(s, d, collationId);
    assertArrayEquals(expected, result);
  }

  @Test
  public void testStringSplitSQL() throws SparkException {
    // Possible splits
    var empty_match = new UTF8String[] { UTF8String.fromString("") };
    var array_abc = new UTF8String[] { UTF8String.fromString("abc") };
    var array_1a2 = new UTF8String[] { UTF8String.fromString("1a2") };
    var array_AaXbB = new UTF8String[] { UTF8String.fromString("AaXbB") };
    var array_aBcDe = new UTF8String[] { UTF8String.fromString("aBcDe") };
    var array_special = new UTF8String[] { UTF8String.fromString("äb世De") };
    var array_abcde = new UTF8String[] { UTF8String.fromString("äbćδe") };
    var full_match = new UTF8String[] { UTF8String.fromString(""), UTF8String.fromString("") };
    var array_1_2 = new UTF8String[] { UTF8String.fromString("1"), UTF8String.fromString("2") };
    var array_A_B = new UTF8String[] { UTF8String.fromString("A"), UTF8String.fromString("B") };
    var array_a_e = new UTF8String[] { UTF8String.fromString("ä"), UTF8String.fromString("e") };
    var array_Aa_bB = new UTF8String[] { UTF8String.fromString("Aa"), UTF8String.fromString("bB") };
    var array_Turkish_uppercase_dotted_I = new UTF8String[] { UTF8String.fromString("İ") };
    var array_Turkish_lowercase_dotted_i = new UTF8String[] { UTF8String.fromString("i\u0307") };
    var array_i = new UTF8String[] { UTF8String.fromString("i"), UTF8String.fromString("") };
    var array_dot = new UTF8String[] { UTF8String.fromString(""), UTF8String.fromString("\u0307") };
    var array_AiB = new UTF8String[] { UTF8String.fromString("Ai\u0307B") };
    var array_AIB = new UTF8String[] { UTF8String.fromString("AİB") };
    var array_small_nonfinal_sigma = new UTF8String[] { UTF8String.fromString("σ") };
    var array_small_final_sigma = new UTF8String[] { UTF8String.fromString("ς") };
    var array_capital_sigma = new UTF8String[] { UTF8String.fromString("Σ") };
    var array_a_b_c = new UTF8String[] { UTF8String.fromString("a"), UTF8String.fromString("b"),
      UTF8String.fromString("c") };
    var array_emojis = new UTF8String[] { UTF8String.fromString("😀"), UTF8String.fromString("😄") };
    var array_AOB = new UTF8String[] { UTF8String.fromString("A𐐅B") };
    var array_AoB = new UTF8String[] { UTF8String.fromString("A𐐭B") };
    // Empty strings.
    assertStringSplitSQL("", "", "UTF8_BINARY", empty_match);
    assertStringSplitSQL("abc", "", "UTF8_BINARY", array_abc);
    assertStringSplitSQL("", "abc", "UTF8_BINARY", empty_match);
    assertStringSplitSQL("", "", "UNICODE", empty_match);
    assertStringSplitSQL("abc", "", "UNICODE", array_abc);
    assertStringSplitSQL("", "abc", "UNICODE", empty_match);
    assertStringSplitSQL("", "", "UTF8_LCASE", empty_match);
    assertStringSplitSQL("abc", "", "UTF8_LCASE", array_abc);
    assertStringSplitSQL("", "abc", "UTF8_LCASE", empty_match);
    assertStringSplitSQL("", "", "UNICODE_CI", empty_match);
    assertStringSplitSQL("abc", "", "UNICODE_CI", array_abc);
    assertStringSplitSQL("", "abc", "UNICODE_CI", empty_match);
    // Basic tests.
    assertStringSplitSQL("1a2", "a", "UTF8_BINARY", array_1_2);
    assertStringSplitSQL("1a2", "A", "UTF8_BINARY", array_1a2);
    assertStringSplitSQL("1a2", "b", "UTF8_BINARY", array_1a2);
    assertStringSplitSQL("1a2", "1a2", "UNICODE", full_match);
    assertStringSplitSQL("1a2", "1A2", "UNICODE", array_1a2);
    assertStringSplitSQL("1a2", "3b4", "UNICODE", array_1a2);
    assertStringSplitSQL("1a2", "A", "UTF8_LCASE", array_1_2);
    assertStringSplitSQL("1a2", "1A2", "UTF8_LCASE", full_match);
    assertStringSplitSQL("1a2", "X", "UTF8_LCASE", array_1a2);
    assertStringSplitSQL("1a2", "a", "UNICODE_CI", array_1_2);
    assertStringSplitSQL("1a2", "A", "UNICODE_CI", array_1_2);
    assertStringSplitSQL("1a2", "1A2", "UNICODE_CI", full_match);
    assertStringSplitSQL("1a2", "123", "UNICODE_CI", array_1a2);
    // Advanced tests.
    assertStringSplitSQL("äb世De", "b世D", "UTF8_BINARY", array_a_e);
    assertStringSplitSQL("äb世De", "B世d", "UTF8_BINARY", array_special);
    assertStringSplitSQL("äbćδe", "bćδ", "UTF8_BINARY", array_a_e);
    assertStringSplitSQL("äbćδe", "BcΔ", "UTF8_BINARY", array_abcde);
    assertStringSplitSQL("äb世De", "äb世De", "UNICODE", full_match);
    assertStringSplitSQL("äb世De", "äB世de", "UNICODE", array_special);
    assertStringSplitSQL("äbćδe", "äbćδe", "UNICODE", full_match);
    assertStringSplitSQL("äbćδe", "ÄBcΔÉ", "UNICODE", array_abcde);
    assertStringSplitSQL("äb世De", "b世D", "UTF8_LCASE", array_a_e);
    assertStringSplitSQL("äb世De", "B世d", "UTF8_LCASE", array_a_e);
    assertStringSplitSQL("äbćδe", "bćδ", "UTF8_LCASE", array_a_e);
    assertStringSplitSQL("äbćδe", "BcΔ", "UTF8_LCASE", array_abcde);
    assertStringSplitSQL("äb世De", "ab世De", "UNICODE_CI", array_special);
    assertStringSplitSQL("äb世De", "AB世dE", "UNICODE_CI", array_special);
    assertStringSplitSQL("äbćδe", "ÄbćδE", "UNICODE_CI", full_match);
    assertStringSplitSQL("äbćδe", "ÄBcΔÉ", "UNICODE_CI", array_abcde);
    // Case variation.
    assertStringSplitSQL("AaXbB", "x", "UTF8_BINARY", array_AaXbB);
    assertStringSplitSQL("AaXbB", "X", "UTF8_BINARY", array_Aa_bB);
    assertStringSplitSQL("AaXbB", "axb", "UNICODE", array_AaXbB);
    assertStringSplitSQL("AaXbB", "aXb", "UNICODE", array_A_B);
    assertStringSplitSQL("AaXbB", "axb", "UTF8_LCASE", array_A_B);
    assertStringSplitSQL("AaXbB", "AXB", "UTF8_LCASE", array_A_B);
    assertStringSplitSQL("AaXbB", "axb", "UNICODE_CI", array_A_B);
    assertStringSplitSQL("AaXbB", "AxB", "UNICODE_CI", array_A_B);
    // Accent variation.
    assertStringSplitSQL("aBcDe", "bćd", "UTF8_BINARY", array_aBcDe);
    assertStringSplitSQL("aBcDe", "BćD", "UTF8_BINARY", array_aBcDe);
    assertStringSplitSQL("aBcDe", "abćde", "UNICODE", array_aBcDe);
    assertStringSplitSQL("aBcDe", "aBćDe", "UNICODE", array_aBcDe);
    assertStringSplitSQL("aBcDe", "bćd", "UTF8_LCASE", array_aBcDe);
    assertStringSplitSQL("aBcDe", "BĆD", "UTF8_LCASE", array_aBcDe);
    assertStringSplitSQL("aBcDe", "abćde", "UNICODE_CI", array_aBcDe);
    assertStringSplitSQL("aBcDe", "AbĆdE", "UNICODE_CI", array_aBcDe);
    // One-to-many case mapping (e.g. Turkish dotted I).
    assertStringSplitSQL("İ", "i", "UTF8_BINARY", array_Turkish_uppercase_dotted_I);
    assertStringSplitSQL("İ", "i", "UTF8_LCASE", array_Turkish_uppercase_dotted_I);
    assertStringSplitSQL("İ", "i", "UNICODE", array_Turkish_uppercase_dotted_I);
    assertStringSplitSQL("İ", "i", "UNICODE_CI", array_Turkish_uppercase_dotted_I);
    assertStringSplitSQL("İ", "\u0307", "UTF8_BINARY", array_Turkish_uppercase_dotted_I);
    assertStringSplitSQL("İ", "\u0307", "UTF8_LCASE", array_Turkish_uppercase_dotted_I);
    assertStringSplitSQL("İ", "\u0307", "UNICODE", array_Turkish_uppercase_dotted_I);
    assertStringSplitSQL("İ", "\u0307", "UNICODE_CI", array_Turkish_uppercase_dotted_I);
    assertStringSplitSQL("i\u0307", "i", "UTF8_BINARY", array_dot);
    assertStringSplitSQL("i\u0307", "i", "UTF8_LCASE", array_dot);
    assertStringSplitSQL("i\u0307", "i", "UNICODE", array_Turkish_lowercase_dotted_i);
    assertStringSplitSQL("i\u0307", "i", "UNICODE_CI", array_Turkish_lowercase_dotted_i);
    assertStringSplitSQL("i\u0307", "\u0307", "UTF8_BINARY", array_i);
    assertStringSplitSQL("i\u0307", "\u0307", "UTF8_LCASE", array_i);
    assertStringSplitSQL("i\u0307", "\u0307", "UNICODE", array_Turkish_lowercase_dotted_i);
    assertStringSplitSQL("i\u0307", "\u0307", "UNICODE_CI", array_Turkish_lowercase_dotted_i);
    assertStringSplitSQL("AİB", "İ", "UTF8_BINARY", array_A_B);
    assertStringSplitSQL("AİB", "İ", "UTF8_LCASE", array_A_B);
    assertStringSplitSQL("AİB", "İ", "UNICODE", array_A_B);
    assertStringSplitSQL("AİB", "İ", "UNICODE_CI", array_A_B);
    assertStringSplitSQL("AİB", "i\u0307", "UTF8_BINARY", array_AIB);
    assertStringSplitSQL("AİB", "i\u0307", "UTF8_LCASE", array_A_B);
    assertStringSplitSQL("AİB", "i\u0307", "UNICODE", array_AIB);
    assertStringSplitSQL("AİB", "i\u0307", "UNICODE_CI", array_A_B);
    assertStringSplitSQL("Ai\u0307B", "İ", "UTF8_BINARY", array_AiB);
    assertStringSplitSQL("Ai\u0307B", "İ", "UTF8_LCASE", array_A_B);
    assertStringSplitSQL("Ai\u0307B", "İ", "UNICODE", array_AiB);
    assertStringSplitSQL("Ai\u0307B", "İ", "UNICODE_CI", array_A_B);
    assertStringSplitSQL("Ai\u0307B", "i\u0307", "UTF8_BINARY", array_A_B);
    assertStringSplitSQL("Ai\u0307B", "i\u0307", "UTF8_LCASE", array_A_B);
    assertStringSplitSQL("Ai\u0307B", "i\u0307", "UNICODE", array_A_B);
    assertStringSplitSQL("Ai\u0307B", "i\u0307", "UNICODE_CI", array_A_B);
    // Conditional case mapping (e.g. Greek sigmas).
    assertStringSplitSQL("σ", "σ", "UTF8_BINARY", full_match);
    assertStringSplitSQL("σ", "σ", "UTF8_LCASE", full_match);
    assertStringSplitSQL("σ", "σ", "UNICODE", full_match);
    assertStringSplitSQL("σ", "σ", "UNICODE_CI", full_match);
    assertStringSplitSQL("σ", "ς", "UTF8_BINARY", array_small_nonfinal_sigma);
    assertStringSplitSQL("σ", "ς", "UTF8_LCASE", full_match);
    assertStringSplitSQL("σ", "ς", "UNICODE", array_small_nonfinal_sigma);
    assertStringSplitSQL("σ", "ς", "UNICODE_CI", full_match);
    assertStringSplitSQL("σ", "Σ", "UTF8_BINARY", array_small_nonfinal_sigma);
    assertStringSplitSQL("σ", "Σ", "UTF8_LCASE", full_match);
    assertStringSplitSQL("σ", "Σ", "UNICODE", array_small_nonfinal_sigma);
    assertStringSplitSQL("σ", "Σ", "UNICODE_CI", full_match);
    assertStringSplitSQL("ς", "σ", "UTF8_BINARY", array_small_final_sigma);
    assertStringSplitSQL("ς", "σ", "UTF8_LCASE", full_match);
    assertStringSplitSQL("ς", "σ", "UNICODE", array_small_final_sigma);
    assertStringSplitSQL("ς", "σ", "UNICODE_CI", full_match);
    assertStringSplitSQL("ς", "ς", "UTF8_BINARY", full_match);
    assertStringSplitSQL("ς", "ς", "UTF8_LCASE", full_match);
    assertStringSplitSQL("ς", "ς", "UNICODE", full_match);
    assertStringSplitSQL("ς", "ς", "UNICODE_CI", full_match);
    assertStringSplitSQL("ς", "Σ", "UTF8_BINARY", array_small_final_sigma);
    assertStringSplitSQL("ς", "Σ", "UTF8_LCASE", full_match);
    assertStringSplitSQL("ς", "Σ", "UNICODE", array_small_final_sigma);
    assertStringSplitSQL("ς", "Σ", "UNICODE_CI", full_match);
    assertStringSplitSQL("Σ", "σ", "UTF8_BINARY", array_capital_sigma);
    assertStringSplitSQL("Σ", "σ", "UTF8_LCASE", full_match);
    assertStringSplitSQL("Σ", "σ", "UNICODE", array_capital_sigma);
    assertStringSplitSQL("Σ", "σ", "UNICODE_CI", full_match);
    assertStringSplitSQL("Σ", "ς", "UTF8_BINARY", array_capital_sigma);
    assertStringSplitSQL("Σ", "ς", "UTF8_LCASE", full_match);
    assertStringSplitSQL("Σ", "ς", "UNICODE", array_capital_sigma);
    assertStringSplitSQL("Σ", "ς", "UNICODE_CI", full_match);
    assertStringSplitSQL("Σ", "Σ", "UTF8_BINARY", full_match);
    assertStringSplitSQL("Σ", "Σ", "UTF8_LCASE", full_match);
    assertStringSplitSQL("Σ", "Σ", "UNICODE", full_match);
    assertStringSplitSQL("Σ", "Σ", "UNICODE_CI", full_match);
    // Surrogate pairs.
    assertStringSplitSQL("a🙃b🙃c", "🙃", "UTF8_BINARY", array_a_b_c);
    assertStringSplitSQL("a🙃b🙃c", "🙃", "UTF8_LCASE", array_a_b_c);
    assertStringSplitSQL("a🙃b🙃c", "🙃", "UNICODE", array_a_b_c);
    assertStringSplitSQL("a🙃b🙃c", "🙃", "UNICODE_CI", array_a_b_c);
    assertStringSplitSQL("😀😆😃😄", "😆😃", "UTF8_BINARY", array_emojis);
    assertStringSplitSQL("😀😆😃😄", "😆😃", "UTF8_LCASE", array_emojis);
    assertStringSplitSQL("😀😆😃😄", "😆😃", "UNICODE", array_emojis);
    assertStringSplitSQL("😀😆😃😄", "😆😃", "UNICODE_CI", array_emojis);
    assertStringSplitSQL("A𐐅B", "𐐅", "UTF8_BINARY", array_A_B);
    assertStringSplitSQL("A𐐅B", "𐐅", "UTF8_LCASE", array_A_B);
    assertStringSplitSQL("A𐐅B", "𐐅", "UNICODE", array_A_B);
    assertStringSplitSQL("A𐐅B", "𐐅", "UNICODE_CI", array_A_B);
    assertStringSplitSQL("A𐐅B", "𐐭", "UTF8_BINARY", array_AOB);
    assertStringSplitSQL("A𐐅B", "𐐭", "UTF8_LCASE", array_A_B);
    assertStringSplitSQL("A𐐅B", "𐐭", "UNICODE", array_AOB);
    assertStringSplitSQL("A𐐅B", "𐐭", "UNICODE_CI", array_A_B);
    assertStringSplitSQL("A𐐭B", "𐐅", "UTF8_BINARY", array_AoB);
    assertStringSplitSQL("A𐐭B", "𐐅", "UTF8_LCASE", array_A_B);
    assertStringSplitSQL("A𐐭B", "𐐅", "UNICODE", array_AoB);
    assertStringSplitSQL("A𐐭B", "𐐅", "UNICODE_CI", array_A_B);
  }

  private void assertUpper(String target, String collationName, String expected)
          throws SparkException {
    UTF8String target_utf8 = UTF8String.fromString(target);
    UTF8String expected_utf8 = UTF8String.fromString(expected);
    int collationId = CollationFactory.collationNameToId(collationName);
    // Testing the new ICU-based implementation of the Upper function.
    assertEquals(expected_utf8, CollationSupport.Upper.exec(target_utf8, collationId, true));
    // Testing the old JVM-based implementation of the Upper function.
    assertEquals(expected_utf8, CollationSupport.Upper.exec(target_utf8, collationId, false));
    // Note: results should be the same in these tests for both ICU and JVM-based implementations.
  }

  @Test
  public void testUpper() throws SparkException {
    // Edge cases
    assertUpper("", "UTF8_BINARY", "");
    assertUpper("", "UTF8_LCASE", "");
    assertUpper("", "UNICODE", "");
    assertUpper("", "UNICODE_CI", "");
    // Basic tests
    assertUpper("abcde", "UTF8_BINARY", "ABCDE");
    assertUpper("abcde", "UTF8_LCASE", "ABCDE");
    assertUpper("abcde", "UNICODE", "ABCDE");
    assertUpper("abcde", "UNICODE_CI", "ABCDE");
    // Uppercase present
    assertUpper("AbCdE", "UTF8_BINARY", "ABCDE");
    assertUpper("aBcDe", "UTF8_BINARY", "ABCDE");
    assertUpper("AbCdE", "UTF8_LCASE", "ABCDE");
    assertUpper("aBcDe", "UTF8_LCASE", "ABCDE");
    assertUpper("AbCdE", "UNICODE", "ABCDE");
    assertUpper("aBcDe", "UNICODE", "ABCDE");
    assertUpper("AbCdE", "UNICODE_CI", "ABCDE");
    assertUpper("aBcDe", "UNICODE_CI", "ABCDE");
    // Accent letters
    assertUpper("aBćDe","UTF8_BINARY", "ABĆDE");
    assertUpper("aBćDe","UTF8_LCASE", "ABĆDE");
    assertUpper("aBćDe","UNICODE", "ABĆDE");
    assertUpper("aBćDe","UNICODE_CI", "ABĆDE");
    // Variable byte length characters
    assertUpper("ab世De", "UTF8_BINARY", "AB世DE");
    assertUpper("äbćδe", "UTF8_BINARY", "ÄBĆΔE");
    assertUpper("ab世De", "UTF8_LCASE", "AB世DE");
    assertUpper("äbćδe", "UTF8_LCASE", "ÄBĆΔE");
    assertUpper("ab世De", "UNICODE", "AB世DE");
    assertUpper("äbćδe", "UNICODE", "ÄBĆΔE");
    assertUpper("ab世De", "UNICODE_CI", "AB世DE");
    assertUpper("äbćδe", "UNICODE_CI", "ÄBĆΔE");
    // Case-variable character length
    assertUpper("i\u0307o", "UTF8_BINARY","I\u0307O");
    assertUpper("i\u0307o", "UTF8_LCASE","I\u0307O");
    assertUpper("i\u0307o", "UNICODE","I\u0307O");
    assertUpper("i\u0307o", "UNICODE_CI","I\u0307O");
    assertUpper("ß ﬁ ﬃ ﬀ ﬆ ῗ", "UTF8_BINARY","SS FI FFI FF ST \u0399\u0308\u0342");
    assertUpper("ß ﬁ ﬃ ﬀ ﬆ ῗ", "UTF8_LCASE","SS FI FFI FF ST \u0399\u0308\u0342");
    assertUpper("ß ﬁ ﬃ ﬀ ﬆ ῗ", "UNICODE","SS FI FFI FF ST \u0399\u0308\u0342");
    assertUpper("ß ﬁ ﬃ ﬀ ﬆ ῗ", "UNICODE","SS FI FFI FF ST \u0399\u0308\u0342");
  }

  private void assertLower(String target, String collationName, String expected)
          throws SparkException {
    UTF8String target_utf8 = UTF8String.fromString(target);
    UTF8String expected_utf8 = UTF8String.fromString(expected);
    int collationId = CollationFactory.collationNameToId(collationName);
    // Testing the new ICU-based implementation of the Lower function.
    assertEquals(expected_utf8, CollationSupport.Lower.exec(target_utf8, collationId, true));
    // Testing the old JVM-based implementation of the Lower function.
    assertEquals(expected_utf8, CollationSupport.Lower.exec(target_utf8, collationId, false));
    // Note: results should be the same in these tests for both ICU and JVM-based implementations.
  }

  @Test
  public void testLower() throws SparkException {
    // Edge cases
    assertLower("", "UTF8_BINARY", "");
    assertLower("", "UTF8_LCASE", "");
    assertLower("", "UNICODE", "");
    assertLower("", "UNICODE_CI", "");
    // Basic tests
    assertLower("ABCDE", "UTF8_BINARY", "abcde");
    assertLower("ABCDE", "UTF8_LCASE", "abcde");
    assertLower("ABCDE", "UNICODE", "abcde");
    assertLower("ABCDE", "UNICODE_CI", "abcde");
    // Uppercase present
    assertLower("AbCdE", "UTF8_BINARY", "abcde");
    assertLower("aBcDe", "UTF8_BINARY", "abcde");
    assertLower("AbCdE", "UTF8_LCASE", "abcde");
    assertLower("aBcDe", "UTF8_LCASE", "abcde");
    assertLower("AbCdE", "UNICODE", "abcde");
    assertLower("aBcDe", "UNICODE", "abcde");
    assertLower("AbCdE", "UNICODE_CI", "abcde");
    assertLower("aBcDe", "UNICODE_CI", "abcde");
    // Accent letters
    assertLower("AbĆdE","UTF8_BINARY", "abćde");
    assertLower("AbĆdE","UTF8_LCASE", "abćde");
    assertLower("AbĆdE","UNICODE", "abćde");
    assertLower("AbĆdE","UNICODE_CI", "abćde");
    // Variable byte length characters
    assertLower("aB世De", "UTF8_BINARY", "ab世de");
    assertLower("ÄBĆΔE", "UTF8_BINARY", "äbćδe");
    assertLower("aB世De", "UTF8_LCASE", "ab世de");
    assertLower("ÄBĆΔE", "UTF8_LCASE", "äbćδe");
    assertLower("aB世De", "UNICODE", "ab世de");
    assertLower("ÄBĆΔE", "UNICODE", "äbćδe");
    assertLower("aB世De", "UNICODE_CI", "ab世de");
    assertLower("ÄBĆΔE", "UNICODE_CI", "äbćδe");
    // Case-variable character length
    assertLower("İo", "UTF8_BINARY","i\u0307o");
    assertLower("İo", "UTF8_LCASE","i\u0307o");
    assertLower("İo", "UNICODE","i\u0307o");
    assertLower("İo", "UNICODE_CI","i\u0307o");
  }

  private void assertInitCap(String target, String collationName, String expected)
          throws SparkException {
    UTF8String target_utf8 = UTF8String.fromString(target);
    UTF8String expected_utf8 = UTF8String.fromString(expected);
    int collationId = CollationFactory.collationNameToId(collationName);
    // Testing the new ICU-based implementation of the Lower function.
    assertEquals(expected_utf8, CollationSupport.InitCap.exec(target_utf8, collationId, true));
    // Testing the old JVM-based implementation of the Lower function.
    assertEquals(expected_utf8, CollationSupport.InitCap.exec(target_utf8, collationId, false));
    // Note: results should be the same in these tests for both ICU and JVM-based implementations.
  }

  @Test
  public void testInitCap() throws SparkException {
    // Edge cases
    assertInitCap("", "UTF8_BINARY", "");
    assertInitCap("", "UTF8_LCASE", "");
    assertInitCap("", "UNICODE", "");
    assertInitCap("", "UNICODE_CI", "");
    // Basic tests
    assertInitCap("ABCDE", "UTF8_BINARY", "Abcde");
    assertInitCap("ABCDE", "UTF8_LCASE", "Abcde");
    assertInitCap("ABCDE", "UNICODE", "Abcde");
    assertInitCap("ABCDE", "UNICODE_CI", "Abcde");
    // Uppercase present
    assertInitCap("AbCdE", "UTF8_BINARY", "Abcde");
    assertInitCap("aBcDe", "UTF8_BINARY", "Abcde");
    assertInitCap("AbCdE", "UTF8_LCASE", "Abcde");
    assertInitCap("aBcDe", "UTF8_LCASE", "Abcde");
    assertInitCap("AbCdE", "UNICODE", "Abcde");
    assertInitCap("aBcDe", "UNICODE", "Abcde");
    assertInitCap("AbCdE", "UNICODE_CI", "Abcde");
    assertInitCap("aBcDe", "UNICODE_CI", "Abcde");
    // Accent letters
    assertInitCap("AbĆdE", "UTF8_BINARY", "Abćde");
    assertInitCap("AbĆdE", "UTF8_LCASE", "Abćde");
    assertInitCap("AbĆdE", "UNICODE", "Abćde");
    assertInitCap("AbĆdE", "UNICODE_CI", "Abćde");
    // Variable byte length characters
    assertInitCap("aB 世 De", "UTF8_BINARY", "Ab 世 De");
    assertInitCap("ÄBĆΔE", "UTF8_BINARY", "Äbćδe");
    assertInitCap("aB 世 De", "UTF8_LCASE", "Ab 世 De");
    assertInitCap("ÄBĆΔE", "UTF8_LCASE", "Äbćδe");
    assertInitCap("aB 世 De", "UNICODE", "Ab 世 De");
    assertInitCap("ÄBĆΔE", "UNICODE", "Äbćδe");
    assertInitCap("aB 世 de", "UNICODE_CI", "Ab 世 De");
    assertInitCap("ÄBĆΔE", "UNICODE_CI", "Äbćδe");
    // Case-variable character length
    assertInitCap("İo", "UTF8_BINARY", "I\u0307o");
    assertInitCap("İo", "UTF8_LCASE", "İo");
    assertInitCap("İo", "UNICODE", "İo");
    assertInitCap("İo", "UNICODE_CI", "İo");
    assertInitCap("i\u0307o", "UTF8_BINARY", "I\u0307o");
    assertInitCap("i\u0307o", "UTF8_LCASE", "I\u0307o");
    assertInitCap("i\u0307o", "UNICODE", "I\u0307o");
    assertInitCap("i\u0307o", "UNICODE_CI", "I\u0307o");
    // Different possible word boundaries
    assertInitCap("a b c", "UTF8_BINARY", "A B C");
    assertInitCap("a b c", "UNICODE", "A B C");
    assertInitCap("a b c", "UTF8_LCASE", "A B C");
    assertInitCap("a b c", "UNICODE_CI", "A B C");
    assertInitCap("a.b,c", "UTF8_BINARY", "A.b,c");
    assertInitCap("a.b,c", "UNICODE", "A.b,C");
    assertInitCap("a.b,c", "UTF8_LCASE", "A.b,C");
    assertInitCap("a.b,c", "UNICODE_CI", "A.b,C");
    assertInitCap("a. b-c", "UTF8_BINARY", "A. B-c");
    assertInitCap("a. b-c", "UNICODE", "A. B-C");
    assertInitCap("a. b-c", "UTF8_LCASE", "A. B-C");
    assertInitCap("a. b-c", "UNICODE_CI", "A. B-C");
    assertInitCap("a?b世c", "UTF8_BINARY", "A?b世c");
    assertInitCap("a?b世c", "UNICODE", "A?B世C");
    assertInitCap("a?b世c", "UTF8_LCASE", "A?B世C");
    assertInitCap("a?b世c", "UNICODE_CI", "A?B世C");
    // Titlecase characters that are different from uppercase characters
    assertInitCap("ǳǱǲ", "UTF8_BINARY", "ǲǳǳ");
    assertInitCap("ǳǱǲ", "UNICODE", "ǲǳǳ");
    assertInitCap("ǳǱǲ", "UTF8_LCASE", "ǲǳǳ");
    assertInitCap("ǳǱǲ", "UNICODE_CI", "ǲǳǳ");
    assertInitCap("ǆaba ǈubav Ǌegova", "UTF8_BINARY", "ǅaba ǈubav ǋegova");
    assertInitCap("ǆaba ǈubav Ǌegova", "UNICODE", "ǅaba ǈubav ǋegova");
    assertInitCap("ǆaba ǈubav Ǌegova", "UTF8_LCASE", "ǅaba ǈubav ǋegova");
    assertInitCap("ǆaba ǈubav Ǌegova", "UNICODE_CI", "ǅaba ǈubav ǋegova");
    assertInitCap("ß ﬁ ﬃ ﬀ ﬆ ΣΗΜΕΡΙΝΟΣ ΑΣΗΜΕΝΙΟΣ İOTA", "UTF8_BINARY",
      "ß ﬁ ﬃ ﬀ ﬆ Σημερινος Ασημενιος I\u0307ota");
    assertInitCap("ß ﬁ ﬃ ﬀ ﬆ ΣΗΜΕΡΙΝΟΣ ΑΣΗΜΕΝΙΟΣ İOTA", "UTF8_LCASE",
      "Ss Fi Ffi Ff St Σημερινος Ασημενιος İota");
    assertInitCap("ß ﬁ ﬃ ﬀ ﬆ ΣΗΜΕΡΙΝΟΣ ΑΣΗΜΕΝΙΟΣ İOTA", "UNICODE",
      "Ss Fi Ffi Ff St Σημερινος Ασημενιος İota");
    assertInitCap("ß ﬁ ﬃ ﬀ ﬆ ΣΗΜΕΡΙΝΟΣ ΑΣΗΜΕΝΙΟΣ İOTA", "UNICODE_CI",
      "Ss Fi Ffi Ff St Σημερινος Ασημενιος İota");
  }

  /**
   * Verify the behaviour of the `StringInstr` collation support class.
   */

  private void assertStringInstr(String string, String substring,
      String collationName, int expected) throws SparkException {
    UTF8String str = UTF8String.fromString(string);
    UTF8String substr = UTF8String.fromString(substring);
    int collationId = CollationFactory.collationNameToId(collationName);
    assertEquals(expected, CollationSupport.StringInstr.exec(str, substr, collationId) + 1);
  }

  @Test
  public void testStringInstr() throws SparkException {
    // Empty strings.
    assertStringInstr("", "", "UTF8_BINARY", 1);
    assertStringInstr("", "", "UTF8_LCASE", 1);
    assertStringInstr("", "", "UNICODE_CI", 1);
    assertStringInstr("", "", "UNICODE", 1);
    assertStringInstr("a", "", "UTF8_BINARY", 1);
    assertStringInstr("a", "", "UTF8_LCASE", 1);
    assertStringInstr("a", "", "UNICODE", 1);
    assertStringInstr("a", "", "UNICODE_CI", 1);
    assertStringInstr("", "x", "UTF8_BINARY", 0);
    assertStringInstr("", "x", "UTF8_LCASE", 0);
    assertStringInstr("", "x", "UNICODE", 0);
    assertStringInstr("", "x", "UNICODE_CI", 0);
    // Basic tests.
    assertStringInstr("aaads", "aa", "UTF8_BINARY", 1);
    assertStringInstr("aaads", "aa", "UTF8_LCASE", 1);
    assertStringInstr("aaads", "aa", "UNICODE", 1);
    assertStringInstr("aaads", "aa", "UNICODE_CI", 1);
    assertStringInstr("aaads", "ds", "UTF8_BINARY", 4);
    assertStringInstr("aaads", "ds", "UTF8_LCASE", 4);
    assertStringInstr("aaads", "ds", "UNICODE", 4);
    assertStringInstr("aaads", "ds", "UNICODE_CI", 4);
    assertStringInstr("aaads", "Aa", "UTF8_BINARY", 0);
    assertStringInstr("aaads", "Aa", "UTF8_LCASE", 1);
    assertStringInstr("aaads", "Aa", "UNICODE", 0);
    assertStringInstr("aaads", "Aa", "UNICODE_CI", 1);
    assertStringInstr("aaaDs", "de", "UTF8_BINARY", 0);
    assertStringInstr("aaaDs", "de", "UTF8_LCASE", 0);
    assertStringInstr("aaaDs", "de", "UNICODE", 0);
    assertStringInstr("aaaDs", "de", "UNICODE_CI", 0);
    assertStringInstr("aaaDs", "ds", "UTF8_BINARY", 0);
    assertStringInstr("aaaDs", "ds", "UTF8_LCASE", 4);
    assertStringInstr("aaaDs", "ds", "UNICODE", 0);
    assertStringInstr("aaaDs", "ds", "UNICODE_CI", 4);
    assertStringInstr("aaadS", "Ds", "UTF8_BINARY", 0);
    assertStringInstr("aaadS", "Ds", "UTF8_LCASE", 4);
    assertStringInstr("aaadS", "Ds", "UNICODE", 0);
    assertStringInstr("aaadS", "Ds", "UNICODE_CI", 4);
    // Advanced tests.
    assertStringInstr("test大千世界X大千世界", "大千", "UTF8_BINARY", 5);
    assertStringInstr("test大千世界X大千世界", "大千", "UTF8_LCASE", 5);
    assertStringInstr("test大千世界X大千世界", "大千", "UNICODE", 5);
    assertStringInstr("test大千世界X大千世界", "大千", "UNICODE_CI", 5);
    assertStringInstr("test大千世界X大千世界", "界X", "UTF8_BINARY", 8);
    assertStringInstr("test大千世界X大千世界", "界X", "UTF8_LCASE", 8);
    assertStringInstr("test大千世界X大千世界", "界X", "UNICODE", 8);
    assertStringInstr("test大千世界X大千世界", "界X", "UNICODE_CI", 8);
    assertStringInstr("test大千世界X大千世界", "界x", "UTF8_BINARY", 0);
    assertStringInstr("test大千世界X大千世界", "界x", "UTF8_LCASE", 8);
    assertStringInstr("test大千世界X大千世界", "界x", "UNICODE", 0);
    assertStringInstr("test大千世界X大千世界", "界x", "UNICODE_CI", 8);
    assertStringInstr("test大千世界X大千世界", "界y", "UTF8_BINARY", 0);
    assertStringInstr("test大千世界X大千世界", "界y", "UTF8_LCASE", 0);
    assertStringInstr("test大千世界X大千世界", "界y", "UNICODE", 0);
    assertStringInstr("test大千世界X大千世界", "界y", "UNICODE_CI", 0);
    // One-to-many case mapping (e.g. Turkish dotted I).
    assertStringInstr("i\u0307", "i", "UNICODE_CI", 0);
    assertStringInstr("i\u0307", "\u0307", "UNICODE_CI", 0);
    assertStringInstr("i\u0307", "İ", "UNICODE_CI", 1);
    assertStringInstr("İ", "i", "UNICODE_CI", 0);
    assertStringInstr("İoi̇o12", "i\u0307o", "UNICODE_CI", 1);
    assertStringInstr("i̇oİo12", "İo", "UNICODE_CI", 1);
    assertStringInstr("abİoi̇o", "i\u0307o", "UNICODE_CI", 3);
    assertStringInstr("abi̇oİo", "İo", "UNICODE_CI", 3);
    assertStringInstr("ai̇oxXİo", "Xx", "UNICODE_CI", 5);
    assertStringInstr("aİoi̇oxx", "XX", "UNICODE_CI", 7);
    assertStringInstr("i\u0307", "i", "UTF8_LCASE", 1); // != UNICODE_CI
    assertStringInstr("i\u0307", "\u0307", "UTF8_LCASE", 2); // != UNICODE_CI
    assertStringInstr("i\u0307", "İ", "UTF8_LCASE", 1);
    assertStringInstr("İ", "i", "UTF8_LCASE", 0);
    assertStringInstr("İoi̇o12", "i\u0307o", "UTF8_LCASE", 1);
    assertStringInstr("i̇oİo12", "İo", "UTF8_LCASE", 1);
    assertStringInstr("abİoi̇o", "i\u0307o", "UTF8_LCASE", 3);
    assertStringInstr("abi̇oİo", "İo", "UTF8_LCASE", 3);
    assertStringInstr("abI\u0307oi̇o", "İo", "UTF8_LCASE", 3);
    assertStringInstr("ai̇oxXİo", "Xx", "UTF8_LCASE", 5);
    assertStringInstr("abİoi̇o", "\u0307o", "UTF8_LCASE", 6);
    assertStringInstr("aİoi̇oxx", "XX", "UTF8_LCASE", 7);
    // Conditional case mapping (e.g. Greek sigmas).
    assertStringInstr("σ", "σ", "UTF8_BINARY", 1);
    assertStringInstr("σ", "ς", "UTF8_BINARY", 0);
    assertStringInstr("σ", "Σ", "UTF8_BINARY", 0);
    assertStringInstr("ς", "σ", "UTF8_BINARY", 0);
    assertStringInstr("ς", "ς", "UTF8_BINARY", 1);
    assertStringInstr("ς", "Σ", "UTF8_BINARY", 0);
    assertStringInstr("Σ", "σ", "UTF8_BINARY", 0);
    assertStringInstr("Σ", "ς", "UTF8_BINARY", 0);
    assertStringInstr("Σ", "Σ", "UTF8_BINARY", 1);
    assertStringInstr("σ", "σ", "UTF8_LCASE", 1);
    assertStringInstr("σ", "ς", "UTF8_LCASE", 1);
    assertStringInstr("σ", "Σ", "UTF8_LCASE", 1);
    assertStringInstr("ς", "σ", "UTF8_LCASE", 1);
    assertStringInstr("ς", "ς", "UTF8_LCASE", 1);
    assertStringInstr("ς", "Σ", "UTF8_LCASE", 1);
    assertStringInstr("Σ", "σ", "UTF8_LCASE", 1);
    assertStringInstr("Σ", "ς", "UTF8_LCASE", 1);
    assertStringInstr("Σ", "Σ", "UTF8_LCASE", 1);
    assertStringInstr("σ", "σ", "UNICODE", 1);
    assertStringInstr("σ", "ς", "UNICODE", 0);
    assertStringInstr("σ", "Σ", "UNICODE", 0);
    assertStringInstr("ς", "σ", "UNICODE", 0);
    assertStringInstr("ς", "ς", "UNICODE", 1);
    assertStringInstr("ς", "Σ", "UNICODE", 0);
    assertStringInstr("Σ", "σ", "UNICODE", 0);
    assertStringInstr("Σ", "ς", "UNICODE", 0);
    assertStringInstr("Σ", "Σ", "UNICODE", 1);
    assertStringInstr("σ", "σ", "UNICODE_CI", 1);
    assertStringInstr("σ", "ς", "UNICODE_CI", 1);
    assertStringInstr("σ", "Σ", "UNICODE_CI", 1);
    assertStringInstr("ς", "σ", "UNICODE_CI", 1);
    assertStringInstr("ς", "ς", "UNICODE_CI", 1);
    assertStringInstr("ς", "Σ", "UNICODE_CI", 1);
    assertStringInstr("Σ", "σ", "UNICODE_CI", 1);
    assertStringInstr("Σ", "ς", "UNICODE_CI", 1);
    assertStringInstr("Σ", "Σ", "UNICODE_CI", 1);
    // Surrogate pairs.
    assertStringInstr("a🙃b", "a", "UTF8_BINARY", 1);
    assertStringInstr("a🙃b", "a", "UTF8_LCASE", 1);
    assertStringInstr("a🙃b", "a", "UNICODE", 1);
    assertStringInstr("a🙃b", "a", "UNICODE_CI", 1);
    assertStringInstr("a🙃b", "🙃", "UTF8_BINARY", 2);
    assertStringInstr("a🙃b", "🙃", "UTF8_LCASE", 2);
    assertStringInstr("a🙃b", "🙃", "UNICODE", 2);
    assertStringInstr("a🙃b", "🙃", "UNICODE_CI", 2);
    assertStringInstr("a🙃b", "b", "UTF8_BINARY", 3);
    assertStringInstr("a🙃b", "b", "UTF8_LCASE", 3);
    assertStringInstr("a🙃b", "b", "UNICODE", 3);
    assertStringInstr("a🙃b", "b", "UNICODE_CI", 3);
    assertStringInstr("a🙃🙃b", "🙃", "UTF8_BINARY", 2);
    assertStringInstr("a🙃🙃b", "🙃", "UTF8_LCASE", 2);
    assertStringInstr("a🙃🙃b", "🙃", "UNICODE", 2);
    assertStringInstr("a🙃🙃b", "🙃", "UNICODE_CI", 2);
    assertStringInstr("a🙃🙃b", "b", "UTF8_BINARY", 4);
    assertStringInstr("a🙃🙃b", "b", "UTF8_LCASE", 4);
    assertStringInstr("a🙃🙃b", "b", "UNICODE", 4);
    assertStringInstr("a🙃🙃b", "b", "UNICODE_CI", 4);
    assertStringInstr("a🙃x🙃b", "b", "UTF8_BINARY", 5);
    assertStringInstr("a🙃x🙃b", "b", "UTF8_LCASE", 5);
    assertStringInstr("a🙃x🙃b", "b", "UNICODE", 5);
    assertStringInstr("a🙃x🙃b", "b", "UNICODE_CI", 5);
  }

  private void assertFindInSet(String word, UTF8String set, String collationName,
      Integer expected) throws SparkException {
    UTF8String w = UTF8String.fromString(word);
    int collationId = CollationFactory.collationNameToId(collationName);
    assertEquals(expected, CollationSupport.FindInSet.exec(w, set, collationId));
  }

  @Test
  public void testFindInSet() throws SparkException {
    assertFindInSet("AB", UTF8String.fromString("abc,b,ab,c,def"), "UTF8_BINARY", 0);
    assertFindInSet("abc", UTF8String.fromString("abc,b,ab,c,def"), "UTF8_BINARY", 1);
    assertFindInSet("def", UTF8String.fromString("abc,b,ab,c,def"), "UTF8_BINARY", 5);
    assertFindInSet("d,ef", UTF8String.fromString("abc,b,ab,c,def"), "UTF8_BINARY", 0);
    assertFindInSet("", UTF8String.fromString("abc,b,ab,c,def"), "UTF8_BINARY", 0);
    assertFindInSet("", UTF8String.fromString(",abc,b,ab,c,def"), "UTF8_BINARY", 1);
    assertFindInSet("", UTF8String.fromString("abc,b,ab,c,def,"), "UTF8_BINARY", 6);
    assertFindInSet("", UTF8String.fromString("abc"), "UTF8_BINARY", 0);
    assertFindInSet("a", UTF8String.fromString("abc,b,ab,c,def"), "UTF8_LCASE", 0);
    assertFindInSet("c", UTF8String.fromString("abc,b,ab,c,def"), "UTF8_LCASE", 4);
    assertFindInSet("AB", UTF8String.fromString("abc,b,ab,c,def"), "UTF8_LCASE", 3);
    assertFindInSet("AbC", UTF8String.fromString("abc,b,ab,c,def"), "UTF8_LCASE", 1);
    assertFindInSet("abcd", UTF8String.fromString("abc,b,ab,c,def"), "UTF8_LCASE", 0);
    assertFindInSet("d,ef", UTF8String.fromString("abc,b,ab,c,def"), "UTF8_LCASE", 0);
    assertFindInSet("XX", UTF8String.fromString("xx"), "UTF8_LCASE", 1);
    assertFindInSet("", UTF8String.fromString("abc,b,ab,c,def"), "UTF8_LCASE", 0);
    assertFindInSet("", UTF8String.fromString(",abc,b,ab,c,def"), "UTF8_LCASE", 1);
    assertFindInSet("", UTF8String.fromString("abc,b,ab,c,def,"), "UTF8_LCASE", 6);
    assertFindInSet("", UTF8String.fromString("abc"), "UTF8_LCASE", 0);
    assertFindInSet("界x", UTF8String.fromString("test,大千,世,界X,大,千,世界"), "UTF8_LCASE", 4);
    assertFindInSet("a", UTF8String.fromString("abc,b,ab,c,def"), "UNICODE", 0);
    assertFindInSet("ab", UTF8String.fromString("abc,b,ab,c,def"), "UNICODE", 3);
    assertFindInSet("Ab", UTF8String.fromString("abc,b,ab,c,def"), "UNICODE", 0);
    assertFindInSet("d,ef", UTF8String.fromString("abc,b,ab,c,def"), "UNICODE", 0);
    assertFindInSet("", UTF8String.fromString(",abc,b,ab,c,def"), "UNICODE", 1);
    assertFindInSet("", UTF8String.fromString("abc,b,ab,c,def,"), "UNICODE", 6);
    assertFindInSet("", UTF8String.fromString("abc"), "UNICODE", 0);
    assertFindInSet("xx", UTF8String.fromString("xx"), "UNICODE", 1);
    assertFindInSet("界x", UTF8String.fromString("test,大千,世,界X,大,千,世界"), "UNICODE", 0);
    assertFindInSet("大", UTF8String.fromString("test,大千,世,界X,大,千,世界"), "UNICODE", 5);
    assertFindInSet("a", UTF8String.fromString("abc,b,ab,c,def"), "UNICODE_CI", 0);
    assertFindInSet("C", UTF8String.fromString("abc,b,ab,c,def"), "UNICODE_CI", 4);
    assertFindInSet("DeF", UTF8String.fromString("abc,b,ab,c,dEf"), "UNICODE_CI", 5);
    assertFindInSet("DEFG", UTF8String.fromString("abc,b,ab,c,def"), "UNICODE_CI", 0);
    assertFindInSet("", UTF8String.fromString(",abc,b,ab,c,def"), "UNICODE_CI", 1);
    assertFindInSet("", UTF8String.fromString("abc,b,ab,c,def,"), "UNICODE_CI", 6);
    assertFindInSet("", UTF8String.fromString("abc"), "UNICODE_CI", 0);
    assertFindInSet("XX", UTF8String.fromString("xx"), "UNICODE_CI", 1);
    assertFindInSet("界x", UTF8String.fromString("test,大千,世,界X,大,千,世界"), "UNICODE_CI", 4);
    assertFindInSet("界x", UTF8String.fromString("test,大千,界Xx,世,界X,大,千,世界"), "UNICODE_CI", 5);
    assertFindInSet("大", UTF8String.fromString("test,大千,世,界X,大,千,世界"), "UNICODE_CI", 5);
    assertFindInSet("i̇", UTF8String.fromString("İ"), "UNICODE_CI", 1);
    assertFindInSet("i", UTF8String.fromString("İ"), "UNICODE_CI", 0);
    assertFindInSet("i̇", UTF8String.fromString("i̇"), "UNICODE_CI", 1);
    assertFindInSet("i", UTF8String.fromString("i̇"), "UNICODE_CI", 0);
    assertFindInSet("i̇", UTF8String.fromString("İ,"), "UNICODE_CI", 1);
    assertFindInSet("i", UTF8String.fromString("İ,"), "UNICODE_CI", 0);
    assertFindInSet("i̇", UTF8String.fromString("i̇,"), "UNICODE_CI", 1);
    assertFindInSet("i", UTF8String.fromString("i̇,"), "UNICODE_CI", 0);
    assertFindInSet("i̇", UTF8String.fromString("ab,İ"), "UNICODE_CI", 2);
    assertFindInSet("i", UTF8String.fromString("ab,İ"), "UNICODE_CI", 0);
    assertFindInSet("i̇", UTF8String.fromString("ab,i̇"), "UNICODE_CI", 2);
    assertFindInSet("i", UTF8String.fromString("ab,i̇"), "UNICODE_CI", 0);
    assertFindInSet("i̇", UTF8String.fromString("ab,İ,12"), "UNICODE_CI", 2);
    assertFindInSet("i", UTF8String.fromString("ab,İ,12"), "UNICODE_CI", 0);
    assertFindInSet("i̇", UTF8String.fromString("ab,i̇,12"), "UNICODE_CI", 2);
    assertFindInSet("i", UTF8String.fromString("ab,i̇,12"), "UNICODE_CI", 0);
    assertFindInSet("i̇o", UTF8String.fromString("ab,İo,12"), "UNICODE_CI", 2);
    assertFindInSet("İo", UTF8String.fromString("ab,i̇o,12"), "UNICODE_CI", 2);
    assertFindInSet("i̇", UTF8String.fromString("İ"), "UTF8_LCASE", 1);
    assertFindInSet("i", UTF8String.fromString("İ"), "UTF8_LCASE", 0);
    assertFindInSet("i̇", UTF8String.fromString("i̇"), "UTF8_LCASE", 1);
    assertFindInSet("i", UTF8String.fromString("i̇"), "UTF8_LCASE", 0);
    assertFindInSet("i̇", UTF8String.fromString("İ,"), "UTF8_LCASE", 1);
    assertFindInSet("i", UTF8String.fromString("İ,"), "UTF8_LCASE", 0);
    assertFindInSet("i̇", UTF8String.fromString("i̇,"), "UTF8_LCASE", 1);
    assertFindInSet("i", UTF8String.fromString("i̇,"), "UTF8_LCASE", 0);
    assertFindInSet("i̇", UTF8String.fromString("ab,İ"), "UTF8_LCASE", 2);
    assertFindInSet("i", UTF8String.fromString("ab,İ"), "UTF8_LCASE", 0);
    assertFindInSet("i̇", UTF8String.fromString("ab,i̇"), "UTF8_LCASE", 2);
    assertFindInSet("i", UTF8String.fromString("ab,i̇"), "UTF8_LCASE", 0);
    assertFindInSet("i̇", UTF8String.fromString("ab,İ,12"), "UTF8_LCASE", 2);
    assertFindInSet("i", UTF8String.fromString("ab,İ,12"), "UTF8_LCASE", 0);
    assertFindInSet("i̇", UTF8String.fromString("ab,i̇,12"), "UTF8_LCASE", 2);
    assertFindInSet("i", UTF8String.fromString("ab,i̇,12"), "UTF8_LCASE", 0);
    assertFindInSet("i̇o", UTF8String.fromString("ab,İo,12"), "UTF8_LCASE", 2);
    assertFindInSet("İo", UTF8String.fromString("ab,i̇o,12"), "UTF8_LCASE", 2);
    // Invalid UTF8 strings
    assertFindInSet("C", UTF8String.fromBytes(
      new byte[] { 0x41, (byte) 0xC2, 0x2C, 0x42, 0x2C, 0x43, 0x2C, 0x43, 0x2C, 0x56 }),
      "UTF8_BINARY", 3);
    assertFindInSet("c", UTF8String.fromBytes(
      new byte[] { 0x41, (byte) 0xC2, 0x2C, 0x42, 0x2C, 0x43, 0x2C, 0x43, 0x2C, 0x56 }),
      "UTF8_LCASE", 2);
    assertFindInSet("C", UTF8String.fromBytes(
      new byte[] { 0x41, (byte) 0xC2, 0x2C, 0x42, 0x2C, 0x43, 0x2C, 0x43, 0x2C, 0x56 }),
      "UNICODE", 2);
    assertFindInSet("c", UTF8String.fromBytes(
      new byte[] { 0x41, (byte) 0xC2, 0x2C, 0x42, 0x2C, 0x43, 0x2C, 0x43, 0x2C, 0x56 }),
      "UNICODE_CI", 2);
    // Greek sigmas.
    assertFindInSet("σ", UTF8String.fromString("σ"), "UTF8_BINARY", 1);
    assertFindInSet("σ", UTF8String.fromString("ς"), "UTF8_BINARY", 0);
    assertFindInSet("σ", UTF8String.fromString("Σ"), "UTF8_BINARY", 0);
    assertFindInSet("ς", UTF8String.fromString("σ"), "UTF8_BINARY", 0);
    assertFindInSet("ς", UTF8String.fromString("ς"), "UTF8_BINARY", 1);
    assertFindInSet("ς", UTF8String.fromString("Σ"), "UTF8_BINARY", 0);
    assertFindInSet("Σ", UTF8String.fromString("σ"), "UTF8_BINARY", 0);
    assertFindInSet("Σ", UTF8String.fromString("ς"), "UTF8_BINARY", 0);
    assertFindInSet("Σ", UTF8String.fromString("Σ"), "UTF8_BINARY", 1);
    assertFindInSet("σ", UTF8String.fromString("σ"), "UTF8_LCASE", 1);
    assertFindInSet("σ", UTF8String.fromString("ς"), "UTF8_LCASE", 1);
    assertFindInSet("σ", UTF8String.fromString("Σ"), "UTF8_LCASE", 1);
    assertFindInSet("ς", UTF8String.fromString("σ"), "UTF8_LCASE", 1);
    assertFindInSet("ς", UTF8String.fromString("ς"), "UTF8_LCASE", 1);
    assertFindInSet("ς", UTF8String.fromString("Σ"), "UTF8_LCASE", 1);
    assertFindInSet("Σ", UTF8String.fromString("σ"), "UTF8_LCASE", 1);
    assertFindInSet("Σ", UTF8String.fromString("ς"), "UTF8_LCASE", 1);
    assertFindInSet("Σ", UTF8String.fromString("Σ"), "UTF8_LCASE", 1);
    assertFindInSet("σ", UTF8String.fromString("σ"), "UNICODE", 1);
    assertFindInSet("σ", UTF8String.fromString("ς"), "UNICODE", 0);
    assertFindInSet("σ", UTF8String.fromString("Σ"), "UNICODE", 0);
    assertFindInSet("ς", UTF8String.fromString("σ"), "UNICODE", 0);
    assertFindInSet("ς", UTF8String.fromString("ς"), "UNICODE", 1);
    assertFindInSet("ς", UTF8String.fromString("Σ"), "UNICODE", 0);
    assertFindInSet("Σ", UTF8String.fromString("σ"), "UNICODE", 0);
    assertFindInSet("Σ", UTF8String.fromString("ς"), "UNICODE", 0);
    assertFindInSet("Σ", UTF8String.fromString("Σ"), "UNICODE", 1);
    assertFindInSet("σ", UTF8String.fromString("σ"), "UNICODE_CI", 1);
    assertFindInSet("σ", UTF8String.fromString("ς"), "UNICODE_CI", 1);
    assertFindInSet("σ", UTF8String.fromString("Σ"), "UNICODE_CI", 1);
    assertFindInSet("ς", UTF8String.fromString("σ"), "UNICODE_CI", 1);
    assertFindInSet("ς", UTF8String.fromString("ς"), "UNICODE_CI", 1);
    assertFindInSet("ς", UTF8String.fromString("Σ"), "UNICODE_CI", 1);
    assertFindInSet("Σ", UTF8String.fromString("σ"), "UNICODE_CI", 1);
    assertFindInSet("Σ", UTF8String.fromString("ς"), "UNICODE_CI", 1);
    assertFindInSet("Σ", UTF8String.fromString("Σ"), "UNICODE_CI", 1);
  }

  /**
   * Verify the behaviour of the `StringReplace` collation support class.
   */

  private void assertStringReplace(String source, String search, String replace,
      String collationName, String expected) throws SparkException {
    UTF8String src = UTF8String.fromString(source);
    UTF8String sear = UTF8String.fromString(search);
    UTF8String repl = UTF8String.fromString(replace);
    int collationId = CollationFactory.collationNameToId(collationName);
    UTF8String result = CollationSupport.StringReplace.exec(src, sear, repl, collationId);
    assertEquals(UTF8String.fromString(expected), result);
  }

  @Test
  public void testStringReplace() throws SparkException {
    // Empty strings.
    assertStringReplace("", "", "", "UTF8_BINARY", "");
    assertStringReplace("", "", "", "UTF8_LCASE", "");
    assertStringReplace("", "", "", "UNICODE", "");
    assertStringReplace("", "", "", "UNICODE_CI", "");
    assertStringReplace("abc", "", "", "UTF8_BINARY", "abc");
    assertStringReplace("abc", "", "", "UTF8_LCASE", "abc");
    assertStringReplace("abc", "", "", "UNICODE", "abc");
    assertStringReplace("abc", "", "", "UNICODE_CI", "abc");
    assertStringReplace("", "x", "", "UTF8_BINARY", "");
    assertStringReplace("", "x", "", "UTF8_LCASE", "");
    assertStringReplace("", "x", "", "UNICODE", "");
    assertStringReplace("", "x", "", "UNICODE_CI", "");
    assertStringReplace("", "", "x", "UTF8_BINARY", "");
    assertStringReplace("", "", "x", "UTF8_LCASE", "");
    assertStringReplace("", "", "x", "UNICODE", "");
    assertStringReplace("", "", "x", "UNICODE_CI", "");
    assertStringReplace("", "b", "x", "UTF8_BINARY", "");
    assertStringReplace("", "b", "x", "UTF8_LCASE", "");
    assertStringReplace("", "b", "x", "UNICODE", "");
    assertStringReplace("", "b", "x", "UNICODE_CI", "");
    assertStringReplace("abc", "b", "", "UTF8_BINARY", "ac");
    assertStringReplace("abc", "b", "", "UTF8_LCASE", "ac");
    assertStringReplace("abc", "b", "", "UNICODE", "ac");
    assertStringReplace("abc", "b", "", "UNICODE_CI", "ac");
    assertStringReplace("abc", "", "x", "UTF8_BINARY", "abc");
    assertStringReplace("abc", "", "x", "UTF8_LCASE", "abc");
    assertStringReplace("abc", "", "x", "UNICODE", "abc");
    assertStringReplace("abc", "", "x", "UNICODE_CI", "abc");
    // Basic tests.
    assertStringReplace("replace", "pl", "", "UTF8_BINARY", "reace");
    assertStringReplace("replace", "pl", "", "UTF8_LCASE", "reace");
    assertStringReplace("replace", "pl", "", "UNICODE", "reace");
    assertStringReplace("replace", "pl", "", "UNICODE_CI", "reace");
    assertStringReplace("replace", "", "123", "UTF8_BINARY", "replace");
    assertStringReplace("replace", "", "123", "UTF8_LCASE", "replace");
    assertStringReplace("replace", "", "123", "UNICODE", "replace");
    assertStringReplace("replace", "", "123", "UNICODE_CI", "replace");
    assertStringReplace("abcabc", "b", "12", "UTF8_BINARY", "a12ca12c");
    assertStringReplace("abcabc", "b", "12", "UTF8_LCASE", "a12ca12c");
    assertStringReplace("abcabc", "b", "12", "UNICODE", "a12ca12c");
    assertStringReplace("abcabc", "b", "12", "UNICODE_CI", "a12ca12c");
    assertStringReplace("replace", "plx", "123", "UTF8_BINARY", "replace");
    assertStringReplace("replace", "plx", "123", "UTF8_LCASE", "replace");
    assertStringReplace("replace", "plx", "123", "UNICODE", "replace");
    assertStringReplace("replace", "plx", "123", "UNICODE_CI", "replace");
    assertStringReplace("Replace", "re", "", "UTF8_BINARY", "Replace");
    assertStringReplace("Replace", "re", "", "UTF8_LCASE", "place");
    assertStringReplace("Replace", "re", "", "UNICODE", "Replace");
    assertStringReplace("Replace", "re", "", "UNICODE_CI", "place");
    assertStringReplace("abcdabcd", "Bc", "", "UTF8_BINARY", "abcdabcd");
    assertStringReplace("abcdabcd", "Bc", "", "UTF8_LCASE", "adad");
    assertStringReplace("abcdabcd", "Bc", "", "UNICODE", "abcdabcd");
    assertStringReplace("abcdabcd", "Bc", "", "UNICODE_CI", "adad");
    assertStringReplace("AbcdabCd", "Bc", "", "UTF8_BINARY", "AbcdabCd");
    assertStringReplace("AbcdabCd", "Bc", "", "UTF8_LCASE", "Adad");
    assertStringReplace("AbcdabCd", "Bc", "", "UNICODE", "AbcdabCd");
    assertStringReplace("AbcdabCd", "Bc", "", "UNICODE_CI", "Adad");
    // Advanced tests.
    assertStringReplace("abcdabcd", "bc", "", "UTF8_BINARY", "adad");
    assertStringReplace("r世eplace", "pl", "123", "UTF8_BINARY", "r世e123ace");
    assertStringReplace("世Replace", "re", "", "UTF8_BINARY", "世Replace");
    assertStringReplace("r世eplace", "pl", "xx", "UTF8_LCASE", "r世exxace");
    assertStringReplace("repl世ace", "PL", "AB", "UTF8_LCASE", "reAB世ace");
    assertStringReplace("re世place", "世", "x", "UTF8_LCASE", "rexplace");
    assertStringReplace("re世place", "plx", "123", "UNICODE", "re世place");
    assertStringReplace("replace世", "", "123", "UNICODE", "replace世");
    assertStringReplace("aBc世abc", "b", "12", "UNICODE", "aBc世a12c");
    assertStringReplace("aBc世abc", "b", "12", "UNICODE_CI", "a12c世a12c");
    assertStringReplace("a世Bcdabcd", "bC", "", "UNICODE_CI", "a世dad");
    assertStringReplace("repl世ace", "Pl", "", "UNICODE_CI", "re世ace");
    // One-to-many case mapping (e.g. Turkish dotted I).
    assertStringReplace("abi̇12", "i", "X", "UNICODE_CI", "abi̇12");
    assertStringReplace("abi̇12", "\u0307", "X", "UNICODE_CI", "abi̇12");
    assertStringReplace("abi̇12", "İ", "X", "UNICODE_CI", "abX12");
    assertStringReplace("abİ12", "i", "X", "UNICODE_CI", "abİ12");
    assertStringReplace("İi̇İi̇İi̇", "i\u0307", "x", "UNICODE_CI", "xxxxxx");
    assertStringReplace("İi̇İi̇İi̇", "i", "x", "UNICODE_CI", "İi̇İi̇İi̇");
    assertStringReplace("abİo12i̇o", "i\u0307o", "xx", "UNICODE_CI", "abxx12xx");
    assertStringReplace("abi̇o12i̇o", "İo", "yy", "UNICODE_CI", "abyy12yy");
    assertStringReplace("abi̇12", "i", "X", "UTF8_LCASE", "abX\u030712"); // != UNICODE_CI
    assertStringReplace("abi̇12", "\u0307", "X", "UTF8_LCASE", "abiX12"); // != UNICODE_CI
    assertStringReplace("abi̇12", "İ", "X", "UTF8_LCASE", "abX12");
    assertStringReplace("abİ12", "i", "X", "UTF8_LCASE", "abİ12");
    assertStringReplace("İi̇İi̇İi̇", "i\u0307", "x", "UTF8_LCASE", "xxxxxx");
    assertStringReplace("İi̇İi̇İi̇", "i", "x", "UTF8_LCASE",
      "İx\u0307İx\u0307İx\u0307"); // != UNICODE_CI
    assertStringReplace("abİo12i̇o", "i\u0307o", "xx", "UTF8_LCASE", "abxx12xx");
    assertStringReplace("abi̇o12i̇o", "İo", "yy", "UTF8_LCASE", "abyy12yy");
    // Conditional case mapping (e.g. Greek sigmas).
    assertStringReplace("σ", "σ", "x", "UTF8_BINARY", "x");
    assertStringReplace("σ", "ς", "x", "UTF8_BINARY", "σ");
    assertStringReplace("σ", "Σ", "x", "UTF8_BINARY", "σ");
    assertStringReplace("ς", "σ", "x", "UTF8_BINARY", "ς");
    assertStringReplace("ς", "ς", "x", "UTF8_BINARY", "x");
    assertStringReplace("ς", "Σ", "x", "UTF8_BINARY", "ς");
    assertStringReplace("Σ", "σ", "x", "UTF8_BINARY", "Σ");
    assertStringReplace("Σ", "ς", "x", "UTF8_BINARY", "Σ");
    assertStringReplace("Σ", "Σ", "x", "UTF8_BINARY", "x");
    assertStringReplace("σ", "σ", "x", "UTF8_LCASE", "x");
    assertStringReplace("σ", "ς", "x", "UTF8_LCASE", "x");
    assertStringReplace("σ", "Σ", "x", "UTF8_LCASE", "x");
    assertStringReplace("ς", "σ", "x", "UTF8_LCASE", "x");
    assertStringReplace("ς", "ς", "x", "UTF8_LCASE", "x");
    assertStringReplace("ς", "Σ", "x", "UTF8_LCASE", "x");
    assertStringReplace("Σ", "σ", "x", "UTF8_LCASE", "x");
    assertStringReplace("Σ", "ς", "x", "UTF8_LCASE", "x");
    assertStringReplace("Σ", "Σ", "x", "UTF8_LCASE", "x");
    assertStringReplace("σ", "σ", "x", "UNICODE", "x");
    assertStringReplace("σ", "ς", "x", "UNICODE", "σ");
    assertStringReplace("σ", "Σ", "x", "UNICODE", "σ");
    assertStringReplace("ς", "σ", "x", "UNICODE", "ς");
    assertStringReplace("ς", "ς", "x", "UNICODE", "x");
    assertStringReplace("ς", "Σ", "x", "UNICODE", "ς");
    assertStringReplace("Σ", "σ", "x", "UNICODE", "Σ");
    assertStringReplace("Σ", "ς", "x", "UNICODE", "Σ");
    assertStringReplace("Σ", "Σ", "x", "UNICODE", "x");
    assertStringReplace("σ", "σ", "x", "UNICODE_CI", "x");
    assertStringReplace("σ", "ς", "x", "UNICODE_CI", "x");
    assertStringReplace("σ", "Σ", "x", "UNICODE_CI", "x");
    assertStringReplace("ς", "σ", "x", "UNICODE_CI", "x");
    assertStringReplace("ς", "ς", "x", "UNICODE_CI", "x");
    assertStringReplace("ς", "Σ", "x", "UNICODE_CI", "x");
    assertStringReplace("Σ", "σ", "x", "UNICODE_CI", "x");
    assertStringReplace("Σ", "ς", "x", "UNICODE_CI", "x");
    assertStringReplace("Σ", "Σ", "x", "UNICODE_CI", "x");
    // Surrogate pairs.
    assertStringReplace("a🙃b", "a", "x", "UTF8_BINARY", "x🙃b");
    assertStringReplace("a🙃b", "b", "x", "UTF8_BINARY", "a🙃x");
    assertStringReplace("a🙃b", "🙃", "x", "UTF8_BINARY", "axb");
    assertStringReplace("a🙃b", "b", "c", "UTF8_LCASE", "a🙃c");
    assertStringReplace("a🙃b", "b", "x", "UTF8_LCASE", "a🙃x");
    assertStringReplace("a🙃b", "🙃", "x", "UTF8_LCASE", "axb");
    assertStringReplace("a🙃b", "b", "c", "UNICODE", "a🙃c");
    assertStringReplace("a🙃b", "b", "x", "UNICODE", "a🙃x");
    assertStringReplace("a🙃b", "🙃", "x", "UNICODE", "axb");
    assertStringReplace("a🙃b", "b", "c", "UNICODE_CI", "a🙃c");
    assertStringReplace("a🙃b", "b", "x", "UNICODE_CI", "a🙃x");
    assertStringReplace("a🙃b", "🙃", "x", "UNICODE_CI", "axb");
  }

  /**
   * Verify the behaviour of the `StringLocate` collation support class.
   */

  private void assertStringLocate(String substring, String string, int start,
      String collationName, int expected) throws SparkException {
    // Note: When using start < 1, be careful to understand the behavior of the `indexOf`
    // method and the implications of using `indexOf` in the `StringLocate` case class.
    UTF8String substr = UTF8String.fromString(substring);
    UTF8String str = UTF8String.fromString(string);
    int collationId = CollationFactory.collationNameToId(collationName);
    int result = CollationSupport.StringLocate.exec(str, substr, start - 1, collationId) + 1;
    assertEquals(expected, result);
  }

  @Test
  public void testStringLocate() throws SparkException {
    // Empty strings.
    assertStringLocate("", "", -1, "UTF8_BINARY", 1);
    assertStringLocate("", "", -1, "UTF8_LCASE", 1);
    assertStringLocate("", "", -1, "UNICODE", 1);
    assertStringLocate("", "", -1, "UNICODE_CI", 1);
    assertStringLocate("", "", 0, "UTF8_BINARY", 1);
    assertStringLocate("", "", 0, "UTF8_LCASE", 1);
    assertStringLocate("", "", 0, "UNICODE", 1);
    assertStringLocate("", "", 0, "UNICODE_CI", 1);
    assertStringLocate("", "", 1, "UTF8_BINARY", 1);
    assertStringLocate("", "", 1, "UTF8_LCASE", 1);
    assertStringLocate("", "", 1, "UNICODE", 1);
    assertStringLocate("", "", 1, "UNICODE_CI", 1);
    assertStringLocate("a", "", -1, "UTF8_BINARY", 0);
    assertStringLocate("a", "", -1, "UTF8_LCASE", 0);
    assertStringLocate("a", "", -1, "UNICODE", 0);
    assertStringLocate("a", "", -1, "UNICODE_CI", 0);
    assertStringLocate("a", "", 0, "UTF8_BINARY", 0);
    assertStringLocate("a", "", 0, "UTF8_LCASE", 0);
    assertStringLocate("a", "", 0, "UNICODE", 0);
    assertStringLocate("a", "", 0, "UNICODE_CI", 0);
    assertStringLocate("a", "", 1, "UTF8_BINARY", 0);
    assertStringLocate("a", "", 1, "UTF8_LCASE", 0);
    assertStringLocate("a", "", 1, "UNICODE", 0);
    assertStringLocate("a", "", 1, "UNICODE_CI", 0);
    assertStringLocate("", "x", -1, "UTF8_BINARY", 1);
    assertStringLocate("", "x", -1, "UTF8_LCASE", 1);
    assertStringLocate("", "x", -1, "UNICODE", 1);
    assertStringLocate("", "x", -1, "UNICODE_CI", 1);
    assertStringLocate("", "x", 0, "UTF8_BINARY", 1);
    assertStringLocate("", "x", 0, "UTF8_LCASE", 1);
    assertStringLocate("", "x", 0, "UNICODE", 1);
    assertStringLocate("", "x", 0, "UNICODE_CI", 1);
    assertStringLocate("", "x", 1, "UTF8_BINARY", 1);
    assertStringLocate("", "x", 1, "UTF8_LCASE", 1);
    assertStringLocate("", "x", 1, "UNICODE", 1);
    assertStringLocate("", "x", 1, "UNICODE_CI", 1);
    // Basic tests.
    assertStringLocate("aa", "aaads", 1, "UTF8_BINARY", 1);
    assertStringLocate("aa", "aaads", 1, "UTF8_LCASE", 1);
    assertStringLocate("aa", "aaads", 1, "UNICODE", 1);
    assertStringLocate("aa", "aaads", 1, "UNICODE_CI", 1);
    assertStringLocate("aa", "aaads", 2, "UTF8_BINARY", 2);
    assertStringLocate("aa", "aaads", 2, "UTF8_LCASE", 2);
    assertStringLocate("aa", "aaads", 2, "UNICODE", 2);
    assertStringLocate("aa", "aaads", 2, "UNICODE_CI", 2);
    assertStringLocate("aa", "aaads", 3, "UTF8_BINARY", 0);
    assertStringLocate("aa", "aaads", 3, "UTF8_LCASE", 0);
    assertStringLocate("aa", "aaads", 3, "UNICODE", 0);
    assertStringLocate("aa", "aaads", 3, "UNICODE_CI", 0);
    assertStringLocate("Aa", "aaads", 1, "UTF8_BINARY", 0);
    assertStringLocate("Aa", "aaads", 1, "UTF8_LCASE", 1);
    assertStringLocate("Aa", "aaads", 1, "UNICODE", 0);
    assertStringLocate("Aa", "aaads", 1, "UNICODE_CI", 1);
    assertStringLocate("Aa", "aaads", 2, "UTF8_BINARY", 0);
    assertStringLocate("Aa", "aaads", 2, "UTF8_LCASE", 2);
    assertStringLocate("Aa", "aaads", 2, "UNICODE", 0);
    assertStringLocate("Aa", "aaads", 2, "UNICODE_CI", 2);
    assertStringLocate("Aa", "aaads", 3, "UTF8_BINARY", 0);
    assertStringLocate("Aa", "aaads", 3, "UTF8_LCASE", 0);
    assertStringLocate("Aa", "aaads", 3, "UNICODE", 0);
    assertStringLocate("Aa", "aaads", 3, "UNICODE_CI", 0);
    assertStringLocate("Aa", "aAads", 1, "UTF8_BINARY", 2);
    assertStringLocate("Aa", "aAads", 1, "UTF8_LCASE", 1);
    assertStringLocate("Aa", "aAads", 1, "UNICODE", 2);
    assertStringLocate("Aa", "aAads", 1, "UNICODE_CI", 1);
    assertStringLocate("AA", "aaads", 1, "UTF8_BINARY", 0);
    assertStringLocate("AA", "aaads", 1, "UTF8_LCASE", 1);
    assertStringLocate("AA", "aaads", 1, "UNICODE", 0);
    assertStringLocate("AA", "aaads", 1, "UNICODE_CI", 1);
    assertStringLocate("aa", "aAads", 2, "UTF8_BINARY", 0);
    assertStringLocate("aa", "aAads", 2, "UTF8_LCASE", 2);
    assertStringLocate("aa", "aAads", 2, "UNICODE", 0);
    assertStringLocate("aa", "aAads", 2, "UNICODE_CI", 2);
    assertStringLocate("aa", "aaAds", 3, "UTF8_BINARY", 0);
    assertStringLocate("aa", "aaAds", 3, "UTF8_LCASE", 0);
    assertStringLocate("aa", "aaAds", 3, "UNICODE", 0);
    assertStringLocate("aa", "aaAds", 3, "UNICODE_CI", 0);
    assertStringLocate("abC", "abcabc", 1, "UTF8_BINARY", 0);
    assertStringLocate("abC", "abcabc", 1, "UTF8_LCASE", 1);
    assertStringLocate("abC", "abcabc", 1, "UNICODE", 0);
    assertStringLocate("abC", "abcabc", 1, "UNICODE_CI", 1);
    assertStringLocate("abC", "abCabc", 2, "UTF8_BINARY", 0);
    assertStringLocate("abC", "abCabc", 2, "UTF8_LCASE", 4);
    assertStringLocate("abC", "abCabc", 2, "UNICODE", 0);
    assertStringLocate("abC", "abCabc", 2, "UNICODE_CI", 4);
    assertStringLocate("abc", "abcabc", 1, "UTF8_BINARY", 1);
    assertStringLocate("abc", "abcabc", 1, "UTF8_LCASE", 1);
    assertStringLocate("abc", "abcabc", 1, "UNICODE", 1);
    assertStringLocate("abc", "abcabc", 1, "UNICODE_CI", 1);
    assertStringLocate("abc", "abcabc", 2, "UTF8_BINARY", 4);
    assertStringLocate("abc", "abcabc", 2, "UTF8_LCASE", 4);
    assertStringLocate("abc", "abcabc", 2, "UNICODE", 4);
    assertStringLocate("abc", "abcabc", 2, "UNICODE_CI", 4);
    assertStringLocate("abc", "abcabc", 3, "UTF8_BINARY", 4);
    assertStringLocate("abc", "abcabc", 3, "UTF8_LCASE", 4);
    assertStringLocate("abc", "abcabc", 3, "UNICODE", 4);
    assertStringLocate("abc", "abcabc", 3, "UNICODE_CI", 4);
    assertStringLocate("abc", "abcabc", 4, "UTF8_BINARY", 4);
    assertStringLocate("abc", "abcabc", 4, "UTF8_LCASE", 4);
    assertStringLocate("abc", "abcabc", 4, "UNICODE", 4);
    assertStringLocate("abc", "abcabc", 4, "UNICODE_CI", 4);
    assertStringLocate("aa", "Aaads", 1, "UTF8_BINARY", 2);
    assertStringLocate("aa", "Aaads", 1, "UTF8_LCASE", 1);
    assertStringLocate("aa", "Aaads", 1, "UNICODE", 2);
    assertStringLocate("aa", "Aaads", 1, "UNICODE_CI", 1);
    // Advanced tests.
    assertStringLocate("界x", "test大千世界X大千世界", 1, "UTF8_BINARY", 0);
    assertStringLocate("界X", "test大千世界X大千世界", 1, "UTF8_BINARY", 8);
    assertStringLocate("界", "test大千世界X大千世界", 13, "UTF8_BINARY", 13);
    assertStringLocate("界x", "test大千世界X大千世界", 1, "UTF8_LCASE", 8);
    assertStringLocate("界X", "test大千世界Xtest大千世界", 1, "UTF8_LCASE", 8);
    assertStringLocate("界", "test大千世界X大千世界", 13, "UTF8_LCASE", 13);
    assertStringLocate("大千", "test大千世界大千世界", 1, "UTF8_LCASE", 5);
    assertStringLocate("大千", "test大千世界大千世界", 9, "UTF8_LCASE", 9);
    assertStringLocate("大千", "大千世界大千世界", 1, "UTF8_LCASE", 1);
    assertStringLocate("界x", "test大千世界X大千世界", 1, "UNICODE", 0);
    assertStringLocate("界X", "test大千世界X大千世界", 1, "UNICODE", 8);
    assertStringLocate("界", "test大千世界X大千世界", 13, "UNICODE", 13);
    assertStringLocate("界x", "test大千世界X大千世界", 1, "UNICODE_CI", 8);
    assertStringLocate("界", "test大千世界X大千世界", 13, "UNICODE_CI", 13);
    assertStringLocate("大千", "test大千世界大千世界", 1, "UNICODE_CI", 5);
    assertStringLocate("大千", "test大千世界大千世界", 9, "UNICODE_CI", 9);
    assertStringLocate("大千", "大千世界大千世界", 1, "UNICODE_CI", 1);
    // One-to-many case mapping (e.g. Turkish dotted I).
    assertStringLocate("\u0307", "i\u0307", 1, "UTF8_BINARY", 2);
    assertStringLocate("\u0307", "İ", 1, "UTF8_LCASE", 0); // != UTF8_BINARY
    assertStringLocate("i", "i\u0307", 1, "UNICODE_CI", 0);
    assertStringLocate("\u0307", "i\u0307", 1, "UNICODE_CI", 0);
    assertStringLocate("i\u0307", "i", 1, "UNICODE_CI", 0);
    assertStringLocate("İ", "i\u0307", 1, "UNICODE_CI", 1);
    assertStringLocate("İ", "i", 1, "UNICODE_CI", 0);
    assertStringLocate("i", "i\u0307", 1, "UTF8_LCASE", 1); // != UNICODE_CI
    assertStringLocate("\u0307", "i\u0307", 1, "UTF8_LCASE", 2); // != UNICODE_CI
    assertStringLocate("i\u0307", "i", 1, "UTF8_LCASE", 0);
    assertStringLocate("İ", "i\u0307", 1, "UTF8_LCASE", 1);
    assertStringLocate("İ", "i", 1, "UTF8_LCASE", 0);
    assertStringLocate("i\u0307o", "İo世界大千世界", 1, "UNICODE_CI", 1);
    assertStringLocate("i\u0307o", "大千İo世界大千世界", 1, "UNICODE_CI", 3);
    assertStringLocate("i\u0307o", "世界İo大千世界大千İo", 4, "UNICODE_CI", 11);
    assertStringLocate("İo", "i̇o世界大千世界", 1, "UNICODE_CI", 1);
    assertStringLocate("İo", "大千i̇o世界大千世界", 1, "UNICODE_CI", 3);
    assertStringLocate("İo", "世界i̇o大千世界大千i̇o", 4, "UNICODE_CI", 12);
    // Conditional case mapping (e.g. Greek sigmas).
    assertStringLocate("σ", "σ", 1, "UTF8_BINARY", 1);
    assertStringLocate("σ", "ς", 1, "UTF8_BINARY", 0);
    assertStringLocate("σ", "Σ", 1, "UTF8_BINARY", 0);
    assertStringLocate("ς", "σ", 1, "UTF8_BINARY", 0);
    assertStringLocate("ς", "ς", 1, "UTF8_BINARY", 1);
    assertStringLocate("ς", "Σ", 1, "UTF8_BINARY", 0);
    assertStringLocate("Σ", "σ", 1, "UTF8_BINARY", 0);
    assertStringLocate("Σ", "ς", 1, "UTF8_BINARY", 0);
    assertStringLocate("Σ", "Σ", 1, "UTF8_BINARY", 1);
    assertStringLocate("σ", "σ", 1, "UTF8_LCASE", 1);
    assertStringLocate("σ", "ς", 1, "UTF8_LCASE", 1);
    assertStringLocate("σ", "Σ", 1, "UTF8_LCASE", 1);
    assertStringLocate("ς", "σ", 1, "UTF8_LCASE", 1);
    assertStringLocate("ς", "ς", 1, "UTF8_LCASE", 1);
    assertStringLocate("ς", "Σ", 1, "UTF8_LCASE", 1);
    assertStringLocate("Σ", "σ", 1, "UTF8_LCASE", 1);
    assertStringLocate("Σ", "ς", 1, "UTF8_LCASE", 1);
    assertStringLocate("Σ", "Σ", 1, "UTF8_LCASE", 1);
    assertStringLocate("σ", "σ", 1, "UNICODE", 1);
    assertStringLocate("σ", "ς", 1, "UNICODE", 0);
    assertStringLocate("σ", "Σ", 1, "UNICODE", 0);
    assertStringLocate("ς", "σ", 1, "UNICODE", 0);
    assertStringLocate("ς", "ς", 1, "UNICODE", 1);
    assertStringLocate("ς", "Σ", 1, "UNICODE", 0);
    assertStringLocate("Σ", "σ", 1, "UNICODE", 0);
    assertStringLocate("Σ", "ς", 1, "UNICODE", 0);
    assertStringLocate("Σ", "Σ", 1, "UNICODE", 1);
    assertStringLocate("σ", "σ", 1, "UNICODE_CI", 1);
    assertStringLocate("σ", "ς", 1, "UNICODE_CI", 1);
    assertStringLocate("σ", "Σ", 1, "UNICODE_CI", 1);
    assertStringLocate("ς", "σ", 1, "UNICODE_CI", 1);
    assertStringLocate("ς", "ς", 1, "UNICODE_CI", 1);
    assertStringLocate("ς", "Σ", 1, "UNICODE_CI", 1);
    assertStringLocate("Σ", "σ", 1, "UNICODE_CI", 1);
    assertStringLocate("Σ", "ς", 1, "UNICODE_CI", 1);
    assertStringLocate("Σ", "Σ", 1, "UNICODE_CI", 1);
    // Surrogate pairs.
    assertStringLocate("a", "a🙃b", 1, "UTF8_BINARY", 1);
    assertStringLocate("a", "a🙃b", 1, "UTF8_LCASE", 1);
    assertStringLocate("a", "a🙃b", 1, "UNICODE", 1);
    assertStringLocate("a", "a🙃b", 1, "UNICODE_CI", 1);
    assertStringLocate("a", "a🙃b", 2, "UTF8_BINARY", 0);
    assertStringLocate("a", "a🙃b", 2, "UTF8_LCASE", 0);
    assertStringLocate("a", "a🙃b", 2, "UNICODE", 0);
    assertStringLocate("a", "a🙃b", 2, "UNICODE_CI", 0);
    assertStringLocate("a", "a🙃b", 3, "UTF8_BINARY", 0);
    assertStringLocate("a", "a🙃b", 3, "UTF8_LCASE", 0);
    assertStringLocate("a", "a🙃b", 3, "UNICODE", 0);
    assertStringLocate("a", "a🙃b", 3, "UNICODE_CI", 0);
    assertStringLocate("🙃", "a🙃b", 1, "UTF8_BINARY", 2);
    assertStringLocate("🙃", "a🙃b", 1, "UTF8_LCASE", 2);
    assertStringLocate("🙃", "a🙃b", 1, "UNICODE", 2);
    assertStringLocate("🙃", "a🙃b", 1, "UNICODE_CI", 2);
    assertStringLocate("🙃", "a🙃b", 2, "UTF8_BINARY", 2);
    assertStringLocate("🙃", "a🙃b", 2, "UTF8_LCASE", 2);
    assertStringLocate("🙃", "a🙃b", 2, "UNICODE", 2);
    assertStringLocate("🙃", "a🙃b", 2, "UNICODE_CI", 2);
    assertStringLocate("🙃", "a🙃b", 3, "UTF8_BINARY", 0);
    assertStringLocate("🙃", "a🙃b", 3, "UTF8_LCASE", 0);
    assertStringLocate("🙃", "a🙃b", 3, "UNICODE", 0);
    assertStringLocate("🙃", "a🙃b", 3, "UNICODE_CI", 0);
    assertStringLocate("b", "a🙃b", 1, "UTF8_BINARY", 3);
    assertStringLocate("b", "a🙃b", 1, "UTF8_LCASE", 3);
    assertStringLocate("b", "a🙃b", 1, "UNICODE", 3);
    assertStringLocate("b", "a🙃b", 1, "UNICODE_CI", 3);
    assertStringLocate("b", "a🙃b", 2, "UTF8_BINARY", 3);
    assertStringLocate("b", "a🙃b", 2, "UTF8_LCASE", 3);
    assertStringLocate("b", "a🙃b", 2, "UNICODE", 3);
    assertStringLocate("b", "a🙃b", 2, "UNICODE_CI", 3);
    assertStringLocate("b", "a🙃b", 3, "UTF8_BINARY", 3);
    assertStringLocate("b", "a🙃b", 3, "UTF8_LCASE", 3);
    assertStringLocate("b", "a🙃b", 3, "UNICODE", 3);
    assertStringLocate("b", "a🙃b", 3, "UNICODE_CI", 3);
    assertStringLocate("🙃", "a🙃🙃b", 1, "UTF8_BINARY", 2);
    assertStringLocate("🙃", "a🙃🙃b", 1, "UTF8_LCASE", 2);
    assertStringLocate("🙃", "a🙃🙃b", 1, "UNICODE", 2);
    assertStringLocate("🙃", "a🙃🙃b", 1, "UNICODE_CI", 2);
    assertStringLocate("🙃", "a🙃🙃b", 2, "UTF8_BINARY", 2);
    assertStringLocate("🙃", "a🙃🙃b", 2, "UTF8_LCASE", 2);
    assertStringLocate("🙃", "a🙃🙃b", 2, "UNICODE", 2);
    assertStringLocate("🙃", "a🙃🙃b", 2, "UNICODE_CI", 2);
    assertStringLocate("🙃", "a🙃🙃b", 3, "UTF8_BINARY", 3);
    assertStringLocate("🙃", "a🙃🙃b", 3, "UTF8_LCASE", 3);
    assertStringLocate("🙃", "a🙃🙃b", 3, "UNICODE", 3);
    assertStringLocate("🙃", "a🙃🙃b", 3, "UNICODE_CI", 3);
    assertStringLocate("🙃", "a🙃🙃b", 4, "UTF8_BINARY", 0);
    assertStringLocate("🙃", "a🙃🙃b", 4, "UTF8_LCASE", 0);
    assertStringLocate("🙃", "a🙃🙃b", 4, "UNICODE", 0);
    assertStringLocate("🙃", "a🙃🙃b", 4, "UNICODE_CI", 0);
    assertStringLocate("b", "a🙃🙃b", 1, "UTF8_BINARY", 4);
    assertStringLocate("b", "a🙃🙃b", 1, "UTF8_LCASE", 4);
    assertStringLocate("b", "a🙃🙃b", 1, "UNICODE", 4);
    assertStringLocate("b", "a🙃🙃b", 1, "UNICODE_CI", 4);
    assertStringLocate("b", "a🙃🙃b", 2, "UTF8_BINARY", 4);
    assertStringLocate("b", "a🙃🙃b", 2, "UTF8_LCASE", 4);
    assertStringLocate("b", "a🙃🙃b", 2, "UNICODE", 4);
    assertStringLocate("b", "a🙃🙃b", 2, "UNICODE_CI", 4);
    assertStringLocate("b", "a🙃🙃b", 3, "UTF8_BINARY", 4);
    assertStringLocate("b", "a🙃🙃b", 3, "UTF8_LCASE", 4);
    assertStringLocate("b", "a🙃🙃b", 3, "UNICODE", 4);
    assertStringLocate("b", "a🙃🙃b", 3, "UNICODE_CI", 4);
    assertStringLocate("b", "a🙃🙃b", 4, "UTF8_BINARY", 4);
    assertStringLocate("b", "a🙃🙃b", 4, "UTF8_LCASE", 4);
    assertStringLocate("b", "a🙃🙃b", 4, "UNICODE", 4);
    assertStringLocate("b", "a🙃🙃b", 4, "UNICODE_CI", 4);
    assertStringLocate("b", "a🙃x🙃b", 1, "UTF8_BINARY", 5);
    assertStringLocate("b", "a🙃x🙃b", 1, "UTF8_LCASE", 5);
    assertStringLocate("b", "a🙃x🙃b", 1, "UNICODE", 5);
    assertStringLocate("b", "a🙃x🙃b", 1, "UNICODE_CI", 5);
    assertStringLocate("b", "a🙃x🙃b", 2, "UTF8_BINARY", 5);
    assertStringLocate("b", "a🙃x🙃b", 2, "UTF8_LCASE", 5);
    assertStringLocate("b", "a🙃x🙃b", 2, "UNICODE", 5);
    assertStringLocate("b", "a🙃x🙃b", 2, "UNICODE_CI", 5);
    assertStringLocate("b", "a🙃x🙃b", 3, "UTF8_BINARY", 5);
    assertStringLocate("b", "a🙃x🙃b", 3, "UTF8_LCASE", 5);
    assertStringLocate("b", "a🙃x🙃b", 3, "UNICODE", 5);
    assertStringLocate("b", "a🙃x🙃b", 3, "UNICODE_CI", 5);
    assertStringLocate("b", "a🙃x🙃b", 4, "UTF8_BINARY", 5);
    assertStringLocate("b", "a🙃x🙃b", 4, "UTF8_LCASE", 5);
    assertStringLocate("b", "a🙃x🙃b", 4, "UNICODE", 5);
    assertStringLocate("b", "a🙃x🙃b", 4, "UNICODE_CI", 5);
  }

  /**
   * Verify the behaviour of the `SubstringIndex` collation support class.
   */

  private void assertSubstringIndex(String string, String delimiter, int count,
      String collationName, String expected) throws SparkException {
    UTF8String str = UTF8String.fromString(string);
    UTF8String delim = UTF8String.fromString(delimiter);
    int collationId = CollationFactory.collationNameToId(collationName);
    UTF8String result = CollationSupport.SubstringIndex.exec(str, delim, count, collationId);
    assertEquals(UTF8String.fromString(expected), result);
  }

  @Test
  public void testSubstringIndex() throws SparkException {
    // Empty strings.
    assertSubstringIndex("", "", 0, "UTF8_BINARY", "");
    assertSubstringIndex("", "", 0, "UTF8_LCASE", "");
    assertSubstringIndex("", "", 0, "UNICODE", "");
    assertSubstringIndex("", "", 0, "UNICODE_CI", "");
    assertSubstringIndex("", "", 1, "UTF8_BINARY", "");
    assertSubstringIndex("", "", 1, "UTF8_LCASE", "");
    assertSubstringIndex("", "", 1, "UNICODE", "");
    assertSubstringIndex("", "", 1, "UNICODE_CI", "");
    assertSubstringIndex("", "", -1, "UTF8_BINARY", "");
    assertSubstringIndex("", "", -1, "UTF8_LCASE", "");
    assertSubstringIndex("", "", -1, "UNICODE", "");
    assertSubstringIndex("", "", -1, "UNICODE_CI", "");
    assertSubstringIndex("", "x", 0, "UTF8_BINARY", "");
    assertSubstringIndex("", "x", 0, "UTF8_LCASE", "");
    assertSubstringIndex("", "x", 0, "UNICODE", "");
    assertSubstringIndex("", "x", 0, "UNICODE_CI", "");
    assertSubstringIndex("", "x", 1, "UTF8_BINARY", "");
    assertSubstringIndex("", "x", 1, "UTF8_LCASE", "");
    assertSubstringIndex("", "x", 1, "UNICODE", "");
    assertSubstringIndex("", "x", 1, "UNICODE_CI", "");
    assertSubstringIndex("", "x", -1, "UTF8_BINARY", "");
    assertSubstringIndex("", "x", -1, "UTF8_LCASE", "");
    assertSubstringIndex("", "x", -1, "UNICODE", "");
    assertSubstringIndex("", "x", -1, "UNICODE_CI", "");
    assertSubstringIndex("abc", "", 0, "UTF8_BINARY", "");
    assertSubstringIndex("abc", "", 0, "UTF8_LCASE", "");
    assertSubstringIndex("abc", "", 0, "UNICODE", "");
    assertSubstringIndex("abc", "", 0, "UNICODE_CI", "");
    assertSubstringIndex("abc", "", 1, "UTF8_BINARY", "");
    assertSubstringIndex("abc", "", 1, "UTF8_LCASE", "");
    assertSubstringIndex("abc", "", 1, "UNICODE", "");
    assertSubstringIndex("abc", "", 1, "UNICODE_CI", "");
    assertSubstringIndex("abc", "", -1, "UTF8_BINARY", "");
    assertSubstringIndex("abc", "", -1, "UTF8_LCASE", "");
    assertSubstringIndex("abc", "", -1, "UNICODE", "");
    assertSubstringIndex("abc", "", -1, "UNICODE_CI", "");
    // Basic tests.
    assertSubstringIndex("axbxc", "a", 1, "UTF8_BINARY", "");
    assertSubstringIndex("axbxc", "a", 1, "UTF8_LCASE", "");
    assertSubstringIndex("axbxc", "a", 1, "UNICODE", "");
    assertSubstringIndex("axbxc", "a", 1, "UNICODE_CI", "");
    assertSubstringIndex("axbxc", "x", 1, "UTF8_BINARY", "a");
    assertSubstringIndex("axbxc", "x", 1, "UTF8_LCASE", "a");
    assertSubstringIndex("axbxc", "x", 1, "UNICODE", "a");
    assertSubstringIndex("axbxc", "x", 1, "UNICODE_CI", "a");
    assertSubstringIndex("axbxc", "b", 1, "UTF8_BINARY", "ax");
    assertSubstringIndex("axbxc", "b", 1, "UTF8_LCASE", "ax");
    assertSubstringIndex("axbxc", "b", 1, "UNICODE", "ax");
    assertSubstringIndex("axbxc", "b", 1, "UNICODE_CI", "ax");
    assertSubstringIndex("axbxc", "x", 2, "UTF8_BINARY", "axb");
    assertSubstringIndex("axbxc", "x", 2, "UTF8_LCASE", "axb");
    assertSubstringIndex("axbxc", "x", 2, "UNICODE", "axb");
    assertSubstringIndex("axbxc", "x", 2, "UNICODE_CI", "axb");
    assertSubstringIndex("axbxc", "c", 1, "UTF8_BINARY", "axbx");
    assertSubstringIndex("axbxc", "c", 1, "UTF8_LCASE", "axbx");
    assertSubstringIndex("axbxc", "c", 1, "UNICODE", "axbx");
    assertSubstringIndex("axbxc", "c", 1, "UNICODE_CI", "axbx");
    assertSubstringIndex("axbxc", "x", 3, "UTF8_BINARY", "axbxc");
    assertSubstringIndex("axbxc", "x", 3, "UTF8_LCASE", "axbxc");
    assertSubstringIndex("axbxc", "x", 3, "UNICODE", "axbxc");
    assertSubstringIndex("axbxc", "x", 3, "UNICODE_CI", "axbxc");
    assertSubstringIndex("axbxc", "d", 1, "UTF8_BINARY", "axbxc");
    assertSubstringIndex("axbxc", "d", 1, "UTF8_LCASE", "axbxc");
    assertSubstringIndex("axbxc", "d", 1, "UNICODE", "axbxc");
    assertSubstringIndex("axbxc", "d", 1, "UNICODE_CI", "axbxc");
    assertSubstringIndex("axbxc", "c", -1, "UTF8_BINARY", "");
    assertSubstringIndex("axbxc", "c", -1, "UTF8_LCASE", "");
    assertSubstringIndex("axbxc", "c", -1, "UNICODE", "");
    assertSubstringIndex("axbxc", "c", -1, "UNICODE_CI", "");
    assertSubstringIndex("axbxc", "x", -1, "UTF8_BINARY", "c");
    assertSubstringIndex("axbxc", "x", -1, "UTF8_LCASE", "c");
    assertSubstringIndex("axbxc", "x", -1, "UNICODE", "c");
    assertSubstringIndex("axbxc", "x", -1, "UNICODE_CI", "c");
    assertSubstringIndex("axbxc", "b", -1, "UTF8_BINARY", "xc");
    assertSubstringIndex("axbxc", "b", -1, "UTF8_LCASE", "xc");
    assertSubstringIndex("axbxc", "b", -1, "UNICODE", "xc");
    assertSubstringIndex("axbxc", "b", -1, "UNICODE_CI", "xc");
    assertSubstringIndex("axbxc", "x", -2, "UTF8_BINARY", "bxc");
    assertSubstringIndex("axbxc", "x", -2, "UTF8_LCASE", "bxc");
    assertSubstringIndex("axbxc", "x", -2, "UNICODE", "bxc");
    assertSubstringIndex("axbxc", "x", -2, "UNICODE_CI", "bxc");
    assertSubstringIndex("axbxc", "a", -1, "UTF8_BINARY", "xbxc");
    assertSubstringIndex("axbxc", "a", -1, "UTF8_LCASE", "xbxc");
    assertSubstringIndex("axbxc", "a", -1, "UNICODE", "xbxc");
    assertSubstringIndex("axbxc", "a", -1, "UNICODE_CI", "xbxc");
    assertSubstringIndex("axbxc", "x", -3, "UTF8_BINARY", "axbxc");
    assertSubstringIndex("axbxc", "x", -3, "UTF8_LCASE", "axbxc");
    assertSubstringIndex("axbxc", "x", -3, "UNICODE", "axbxc");
    assertSubstringIndex("axbxc", "x", -3, "UNICODE_CI", "axbxc");
    assertSubstringIndex("axbxc", "d", -1, "UTF8_BINARY", "axbxc");
    assertSubstringIndex("axbxc", "d", -1, "UTF8_LCASE", "axbxc");
    assertSubstringIndex("axbxc", "d", -1, "UNICODE", "axbxc");
    assertSubstringIndex("axbxc", "d", -1, "UNICODE_CI", "axbxc");
    // Advanced tests.
    assertSubstringIndex("wwwgapachegorg", "g", -3, "UTF8_BINARY", "apachegorg");
    assertSubstringIndex("www||apache||org", "||", 2, "UTF8_BINARY", "www||apache");
    assertSubstringIndex("aaaaaaaaaa", "aa", 2, "UTF8_BINARY", "a");
    assertSubstringIndex("AaAaAaAaAa", "aa", 2, "UTF8_LCASE", "A");
    assertSubstringIndex("www.apache.org", ".", 3, "UTF8_LCASE", "www.apache.org");
    assertSubstringIndex("wwwXapacheXorg", "x", 2, "UTF8_LCASE", "wwwXapache");
    assertSubstringIndex("wwwxapachexorg", "X", 1, "UTF8_LCASE", "www");
    assertSubstringIndex("www.apache.org", ".", 0, "UTF8_LCASE", "");
    assertSubstringIndex("www.apache.ORG", ".", -3, "UTF8_LCASE", "www.apache.ORG");
    assertSubstringIndex("wwwGapacheGorg", "g", 1, "UTF8_LCASE", "www");
    assertSubstringIndex("wwwGapacheGorg", "g", 3, "UTF8_LCASE", "wwwGapacheGor");
    assertSubstringIndex("gwwwGapacheGorg", "g", 3, "UTF8_LCASE", "gwwwGapache");
    assertSubstringIndex("wwwGapacheGorg", "g", -3, "UTF8_LCASE", "apacheGorg");
    assertSubstringIndex("wwwmapacheMorg", "M", -2, "UTF8_LCASE", "apacheMorg");
    assertSubstringIndex("www.apache.org", ".", -1, "UTF8_LCASE", "org");
    assertSubstringIndex("www.apache.org.", ".", -1, "UTF8_LCASE", "");
    assertSubstringIndex("", ".", -2, "UTF8_LCASE", "");
    assertSubstringIndex("test大千世界X大千世界", "x", -1, "UTF8_LCASE", "大千世界");
    assertSubstringIndex("test大千世界X大千世界", "X", 1, "UTF8_LCASE", "test大千世界");
    assertSubstringIndex("test大千世界大千世界", "千", 2, "UTF8_LCASE", "test大千世界大");
    assertSubstringIndex("www||APACHE||org", "||", 2, "UTF8_LCASE", "www||APACHE");
    assertSubstringIndex("www||APACHE||org", "||", -1, "UTF8_LCASE", "org");
    assertSubstringIndex("AaAaAaAaAa", "Aa", 2, "UNICODE", "Aa");
    assertSubstringIndex("wwwYapacheyorg", "y", 3, "UNICODE", "wwwYapacheyorg");
    assertSubstringIndex("www.apache.org", ".", 2, "UNICODE", "www.apache");
    assertSubstringIndex("wwwYapacheYorg", "Y", 1, "UNICODE", "www");
    assertSubstringIndex("wwwYapacheYorg", "y", 1, "UNICODE", "wwwYapacheYorg");
    assertSubstringIndex("wwwGapacheGorg", "g", 1, "UNICODE", "wwwGapacheGor");
    assertSubstringIndex("GwwwGapacheGorG", "G", 3, "UNICODE", "GwwwGapache");
    assertSubstringIndex("wwwGapacheGorG", "G", -3, "UNICODE", "apacheGorG");
    assertSubstringIndex("www.apache.org", ".", 0, "UNICODE", "");
    assertSubstringIndex("www.apache.org", ".", -3, "UNICODE", "www.apache.org");
    assertSubstringIndex("www.apache.org", ".", -2, "UNICODE", "apache.org");
    assertSubstringIndex("www.apache.org", ".", -1, "UNICODE", "org");
    assertSubstringIndex("", ".", -2, "UNICODE", "");
    assertSubstringIndex("test大千世界X大千世界", "X", -1, "UNICODE", "大千世界");
    assertSubstringIndex("test大千世界X大千世界", "X", 1, "UNICODE", "test大千世界");
    assertSubstringIndex("大x千世界大千世x界", "x", 1, "UNICODE", "大");
    assertSubstringIndex("大x千世界大千世x界", "x", -1, "UNICODE", "界");
    assertSubstringIndex("大x千世界大千世x界", "x", -2, "UNICODE", "千世界大千世x界");
    assertSubstringIndex("大千世界大千世界", "千", 2, "UNICODE", "大千世界大");
    assertSubstringIndex("www||apache||org", "||", 2, "UNICODE", "www||apache");
    assertSubstringIndex("AaAaAaAaAa", "aa", 2, "UNICODE_CI", "A");
    assertSubstringIndex("www.apache.org", ".", 3, "UNICODE_CI", "www.apache.org");
    assertSubstringIndex("wwwXapacheXorg", "x", 2, "UNICODE_CI", "wwwXapache");
    assertSubstringIndex("wwwxapacheXorg", "X", 1, "UNICODE_CI", "www");
    assertSubstringIndex("www.apache.org", ".", 0, "UNICODE_CI", "");
    assertSubstringIndex("wwwGapacheGorg", "G", 3, "UNICODE_CI", "wwwGapacheGor");
    assertSubstringIndex("gwwwGapacheGorg", "g", 3, "UNICODE_CI", "gwwwGapache");
    assertSubstringIndex("gwwwGapacheGorg", "g", -3, "UNICODE_CI", "apacheGorg");
    assertSubstringIndex("www.apache.ORG", ".", -3, "UNICODE_CI", "www.apache.ORG");
    assertSubstringIndex("wwwmapacheMorg", "M", -2, "UNICODE_CI", "apacheMorg");
    assertSubstringIndex("www.apache.org", ".", -1, "UNICODE_CI", "org");
    assertSubstringIndex("", ".", -2, "UNICODE_CI", "");
    assertSubstringIndex("test大千世界X大千世界", "X", -1, "UNICODE_CI", "大千世界");
    assertSubstringIndex("test大千世界X大千世界", "X", 1, "UNICODE_CI", "test大千世界");
    assertSubstringIndex("test大千世界大千世界", "千", 2, "UNICODE_CI", "test大千世界大");
    assertSubstringIndex("www||APACHE||org", "||", 2, "UNICODE_CI", "www||APACHE");
    // One-to-many case mapping (e.g. Turkish dotted I).
    assertSubstringIndex("abİo12", "i\u0307o", 1, "UNICODE_CI", "ab");
    assertSubstringIndex("abİo12", "i\u0307o", -1, "UNICODE_CI", "12");
    assertSubstringIndex("abi̇o12", "İo", 1, "UNICODE_CI", "ab");
    assertSubstringIndex("abi̇o12", "İo", -1, "UNICODE_CI", "12");
    assertSubstringIndex("ai̇bi̇o12", "İo", 1, "UNICODE_CI", "ai̇b");
    assertSubstringIndex("ai̇bi̇o12i̇o", "İo", 2, "UNICODE_CI", "ai̇bi̇o12");
    assertSubstringIndex("ai̇bi̇o12i̇o", "İo", -1, "UNICODE_CI", "");
    assertSubstringIndex("ai̇bi̇o12i̇o", "İo", -2, "UNICODE_CI", "12i̇o");
    assertSubstringIndex("ai̇bi̇oİo12İoi̇o", "İo", -4, "UNICODE_CI", "İo12İoi̇o");
    assertSubstringIndex("ai̇bi̇oİo12İoi̇o", "i\u0307o", -4, "UNICODE_CI", "İo12İoi̇o");
    assertSubstringIndex("ai̇bİoi̇o12i̇oİo", "İo", -4, "UNICODE_CI", "i̇o12i̇oİo");
    assertSubstringIndex("ai̇bİoi̇o12i̇oİo", "i\u0307o", -4, "UNICODE_CI", "i̇o12i̇oİo");
    assertSubstringIndex("abi̇12", "i", 1, "UNICODE_CI", "abi̇12");
    assertSubstringIndex("abi̇12", "\u0307", 1, "UNICODE_CI", "abi̇12");
    assertSubstringIndex("abi̇12", "İ", 1, "UNICODE_CI", "ab");
    assertSubstringIndex("abİ12", "i", 1, "UNICODE_CI", "abİ12");
    assertSubstringIndex("ai̇bi̇oİo12İoi̇o", "İo", -4, "UNICODE_CI", "İo12İoi̇o");
    assertSubstringIndex("ai̇bi̇oİo12İoi̇o", "i\u0307o", -4, "UNICODE_CI", "İo12İoi̇o");
    assertSubstringIndex("ai̇bİoi̇o12i̇oİo", "İo", -4, "UNICODE_CI", "i̇o12i̇oİo");
    assertSubstringIndex("ai̇bİoi̇o12i̇oİo", "i\u0307o", -4, "UNICODE_CI", "i̇o12i̇oİo");
    assertSubstringIndex("ai̇bi̇oİo12İoi̇o", "İo", 3, "UNICODE_CI", "ai̇bi̇oİo12");
    assertSubstringIndex("ai̇bi̇oİo12İoi̇o", "i\u0307o", 3, "UNICODE_CI", "ai̇bi̇oİo12");
    assertSubstringIndex("ai̇bİoi̇o12i̇oİo", "İo", 3, "UNICODE_CI", "ai̇bİoi̇o12");
    assertSubstringIndex("ai̇bİoi̇o12i̇oİo", "i\u0307o", 3, "UNICODE_CI", "ai̇bİoi̇o12");
    assertSubstringIndex("abi̇12", "i", 1, "UTF8_LCASE", "ab"); // != UNICODE_CI
    assertSubstringIndex("abi̇12", "\u0307", 1, "UTF8_LCASE", "abi"); // != UNICODE_CI
    assertSubstringIndex("abi̇12", "İ", 1, "UTF8_LCASE", "ab");
    assertSubstringIndex("abİ12", "i", 1, "UTF8_LCASE", "abİ12");
    assertSubstringIndex("ai̇bi̇oİo12İoi̇o", "İo", -4, "UTF8_LCASE", "İo12İoi̇o");
    assertSubstringIndex("ai̇bi̇oİo12İoi̇o", "i\u0307o", -4, "UTF8_LCASE", "İo12İoi̇o");
    assertSubstringIndex("ai̇bİoi̇o12i̇oİo", "İo", -4, "UTF8_LCASE", "i̇o12i̇oİo");
    assertSubstringIndex("ai̇bİoi̇o12i̇oİo", "i\u0307o", -4, "UTF8_LCASE", "i̇o12i̇oİo");
    assertSubstringIndex("bİoi̇o12i̇o", "\u0307oi", 1, "UTF8_LCASE", "bİoi̇o12i̇o");
    assertSubstringIndex("ai̇bi̇oİo12İoi̇o", "İo", 3, "UTF8_LCASE", "ai̇bi̇oİo12");
    assertSubstringIndex("ai̇bi̇oİo12İoi̇o", "i\u0307o", 3, "UTF8_LCASE", "ai̇bi̇oİo12");
    assertSubstringIndex("ai̇bİoi̇o12i̇oİo", "İo", 3, "UTF8_LCASE", "ai̇bİoi̇o12");
    assertSubstringIndex("ai̇bİoi̇o12i̇oİo", "i\u0307o", 3, "UTF8_LCASE", "ai̇bİoi̇o12");
    assertSubstringIndex("bİoi̇o12i̇o", "\u0307oi", 1, "UTF8_LCASE", "bİoi̇o12i̇o");
    // Conditional case mapping (e.g. Greek sigmas).
    assertSubstringIndex("σ", "σ", 1, "UTF8_BINARY", "");
    assertSubstringIndex("σ", "ς", 1, "UTF8_BINARY", "σ");
    assertSubstringIndex("σ", "Σ", 1, "UTF8_BINARY", "σ");
    assertSubstringIndex("ς", "σ", 1, "UTF8_BINARY", "ς");
    assertSubstringIndex("ς", "ς", 1, "UTF8_BINARY", "");
    assertSubstringIndex("ς", "Σ", 1, "UTF8_BINARY", "ς");
    assertSubstringIndex("Σ", "σ", 1, "UTF8_BINARY", "Σ");
    assertSubstringIndex("Σ", "ς", 1, "UTF8_BINARY", "Σ");
    assertSubstringIndex("Σ", "Σ", 1, "UTF8_BINARY", "");
    assertSubstringIndex("σ", "σ", 1, "UTF8_LCASE", "");
    assertSubstringIndex("σ", "ς", 1, "UTF8_LCASE", "");
    assertSubstringIndex("σ", "Σ", 1, "UTF8_LCASE", "");
    assertSubstringIndex("ς", "σ", 1, "UTF8_LCASE", "");
    assertSubstringIndex("ς", "ς", 1, "UTF8_LCASE", "");
    assertSubstringIndex("ς", "Σ", 1, "UTF8_LCASE", "");
    assertSubstringIndex("Σ", "σ", 1, "UTF8_LCASE", "");
    assertSubstringIndex("Σ", "ς", 1, "UTF8_LCASE", "");
    assertSubstringIndex("Σ", "Σ", 1, "UTF8_LCASE", "");
    assertSubstringIndex("σ", "σ", 1, "UNICODE", "");
    assertSubstringIndex("σ", "ς", 1, "UNICODE", "σ");
    assertSubstringIndex("σ", "Σ", 1, "UNICODE", "σ");
    assertSubstringIndex("ς", "σ", 1, "UNICODE", "ς");
    assertSubstringIndex("ς", "ς", 1, "UNICODE", "");
    assertSubstringIndex("ς", "Σ", 1, "UNICODE", "ς");
    assertSubstringIndex("Σ", "σ", 1, "UNICODE", "Σ");
    assertSubstringIndex("Σ", "ς", 1, "UNICODE", "Σ");
    assertSubstringIndex("Σ", "Σ", 1, "UNICODE", "");
    assertSubstringIndex("σ", "σ", 1, "UNICODE_CI", "");
    assertSubstringIndex("σ", "ς", 1, "UNICODE_CI", "");
    assertSubstringIndex("σ", "Σ", 1, "UNICODE_CI", "");
    assertSubstringIndex("ς", "σ", 1, "UNICODE_CI", "");
    assertSubstringIndex("ς", "ς", 1, "UNICODE_CI", "");
    assertSubstringIndex("ς", "Σ", 1, "UNICODE_CI", "");
    assertSubstringIndex("Σ", "σ", 1, "UNICODE_CI", "");
    assertSubstringIndex("Σ", "ς", 1, "UNICODE_CI", "");
    assertSubstringIndex("Σ", "Σ", 1, "UNICODE_CI", "");
    // Surrogate pairs.
    assertSubstringIndex("a🙃b🙃c", "a", 1, "UTF8_BINARY", "");
    assertSubstringIndex("a🙃b🙃c", "a", 1, "UTF8_LCASE", "");
    assertSubstringIndex("a🙃b🙃c", "a", 1, "UNICODE", "");
    assertSubstringIndex("a🙃b🙃c", "a", 1, "UNICODE_CI", "");
    assertSubstringIndex("a🙃b🙃c", "🙃", 1, "UTF8_BINARY", "a");
    assertSubstringIndex("a🙃b🙃c", "🙃", 1, "UTF8_LCASE", "a");
    assertSubstringIndex("a🙃b🙃c", "🙃", 1, "UNICODE", "a");
    assertSubstringIndex("a🙃b🙃c", "🙃", 1, "UNICODE_CI", "a");
    assertSubstringIndex("a🙃b🙃c", "b", 1, "UTF8_BINARY", "a🙃");
    assertSubstringIndex("a🙃b🙃c", "b", 1, "UTF8_LCASE", "a🙃");
    assertSubstringIndex("a🙃b🙃c", "b", 1, "UNICODE", "a🙃");
    assertSubstringIndex("a🙃b🙃c", "b", 1, "UNICODE_CI", "a🙃");
    assertSubstringIndex("a🙃b🙃c", "🙃", 2, "UTF8_BINARY", "a🙃b");
    assertSubstringIndex("a🙃b🙃c", "🙃", 2, "UTF8_LCASE", "a🙃b");
    assertSubstringIndex("a🙃b🙃c", "🙃", 2, "UNICODE", "a🙃b");
    assertSubstringIndex("a🙃b🙃c", "🙃", 2, "UNICODE_CI", "a🙃b");
    assertSubstringIndex("a🙃b🙃c", "c", 1, "UTF8_BINARY", "a🙃b🙃");
    assertSubstringIndex("a🙃b🙃c", "c", 1, "UTF8_LCASE", "a🙃b🙃");
    assertSubstringIndex("a🙃b🙃c", "c", 1, "UNICODE", "a🙃b🙃");
    assertSubstringIndex("a🙃b🙃c", "c", 1, "UNICODE_CI", "a🙃b🙃");
    assertSubstringIndex("a🙃b🙃c", "🙃", 3, "UTF8_BINARY", "a🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "🙃", 3, "UTF8_LCASE", "a🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "🙃", 3, "UNICODE", "a🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "🙃", 3, "UNICODE_CI", "a🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "d", 1, "UTF8_BINARY", "a🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "d", 1, "UTF8_LCASE", "a🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "d", 1, "UNICODE", "a🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "d", 1, "UNICODE_CI", "a🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "c", -1, "UTF8_BINARY", "");
    assertSubstringIndex("a🙃b🙃c", "c", -1, "UTF8_LCASE", "");
    assertSubstringIndex("a🙃b🙃c", "c", -1, "UNICODE", "");
    assertSubstringIndex("a🙃b🙃c", "c", -1, "UNICODE_CI", "");
    assertSubstringIndex("a🙃b🙃c", "🙃", -1, "UTF8_BINARY", "c");
    assertSubstringIndex("a🙃b🙃c", "🙃", -1, "UTF8_LCASE", "c");
    assertSubstringIndex("a🙃b🙃c", "🙃", -1, "UNICODE", "c");
    assertSubstringIndex("a🙃b🙃c", "🙃", -1, "UNICODE_CI", "c");
    assertSubstringIndex("a🙃b🙃c", "b", -1, "UTF8_BINARY", "🙃c");
    assertSubstringIndex("a🙃b🙃c", "b", -1, "UTF8_LCASE", "🙃c");
    assertSubstringIndex("a🙃b🙃c", "b", -1, "UNICODE", "🙃c");
    assertSubstringIndex("a🙃b🙃c", "b", -1, "UNICODE_CI", "🙃c");
    assertSubstringIndex("a🙃b🙃c", "🙃", -2, "UTF8_BINARY", "b🙃c");
    assertSubstringIndex("a🙃b🙃c", "🙃", -2, "UTF8_LCASE", "b🙃c");
    assertSubstringIndex("a🙃b🙃c", "🙃", -2, "UNICODE", "b🙃c");
    assertSubstringIndex("a🙃b🙃c", "🙃", -2, "UNICODE_CI", "b🙃c");
    assertSubstringIndex("a🙃b🙃c", "a", -1, "UTF8_BINARY", "🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "a", -1, "UTF8_LCASE", "🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "a", -1, "UNICODE", "🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "a", -1, "UNICODE_CI", "🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "🙃", -3, "UTF8_BINARY", "a🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "🙃", -3, "UTF8_LCASE", "a🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "🙃", -3, "UNICODE", "a🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "🙃", -3, "UNICODE_CI", "a🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "d", -1, "UTF8_BINARY", "a🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "d", -1, "UTF8_LCASE", "a🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "d", -1, "UNICODE", "a🙃b🙃c");
    assertSubstringIndex("a🙃b🙃c", "d", -1, "UNICODE_CI", "a🙃b🙃c");
  }

  /**
   * Verify the behaviour of the `StringTrim` collation support class.
   */

  private void assertStringTrim(String collationName, String sourceString, String trimString,
      String expected) throws SparkException {
    // Prepare the input and expected result.
    int collationId = CollationFactory.collationNameToId(collationName);
    UTF8String src = UTF8String.fromString(sourceString);
    UTF8String trim = UTF8String.fromString(trimString);
    UTF8String result, resultTrimLeftRight, resultTrimRightLeft;

    if (trimString == null) {
      // Trim string is ASCII space.
      result = CollationSupport.StringTrim.exec(src);
      UTF8String trimLeft = CollationSupport.StringTrimLeft.exec(src);
      resultTrimLeftRight = CollationSupport.StringTrimRight.exec(trimLeft);
      UTF8String trimRight = CollationSupport.StringTrimRight.exec(src);
      resultTrimRightLeft = CollationSupport.StringTrimLeft.exec(trimRight);
    } else {
      // Trim string is specified.
      result = CollationSupport.StringTrim.exec(src, trim, collationId);
      UTF8String trimLeft = CollationSupport.StringTrimLeft.exec(src, trim, collationId);
      resultTrimLeftRight = CollationSupport.StringTrimRight.exec(trimLeft, trim, collationId);
      UTF8String trimRight = CollationSupport.StringTrimRight.exec(src, trim, collationId);
      resultTrimRightLeft = CollationSupport.StringTrimLeft.exec(trimRight, trim, collationId);
    }

    // Test that StringTrim result is as expected.
    assertEquals(UTF8String.fromString(expected), result);
    // Test that the order of the trims is not important.
    assertEquals(resultTrimLeftRight, result);
    assertEquals(resultTrimRightLeft, result);
  }

  @Test
  public void testStringTrim() throws SparkException {
    // Basic tests.
    assertStringTrim("UTF8_BINARY", "", "", "");
    assertStringTrim("UTF8_BINARY", "", "xyz", "");
    assertStringTrim("UTF8_BINARY", "asd", "", "asd");
    assertStringTrim("UTF8_BINARY", "asd", null, "asd");
    assertStringTrim("UTF8_BINARY", "  asd  ", null, "asd");
    assertStringTrim("UTF8_BINARY", " a世a ", null, "a世a");
    assertStringTrim("UTF8_BINARY", "asd", "x", "asd");
    assertStringTrim("UTF8_BINARY", "xxasdxx", "x", "asd");
    assertStringTrim("UTF8_BINARY", "xa世ax", "x", "a世a");
    assertStringTrim("UTF8_LCASE", "", "", "");
    assertStringTrim("UTF8_LCASE", "", "xyz", "");
    assertStringTrim("UTF8_LCASE", "asd", "", "asd");
    assertStringTrim("UTF8_LCASE", "asd", null, "asd");
    assertStringTrim("UTF8_LCASE", "  asd  ", null, "asd");
    assertStringTrim("UTF8_LCASE", " a世a ", null, "a世a");
    assertStringTrim("UTF8_LCASE", "asd", "x", "asd");
    assertStringTrim("UTF8_LCASE", "xxasdxx", "x", "asd");
    assertStringTrim("UTF8_LCASE", "xa世ax", "x", "a世a");
    assertStringTrim("UNICODE", "", "", "");
    assertStringTrim("UNICODE", "", "xyz", "");
    assertStringTrim("UNICODE", "asd", "", "asd");
    assertStringTrim("UNICODE", "asd", null, "asd");
    assertStringTrim("UNICODE", "  asd  ", null, "asd");
    assertStringTrim("UNICODE", " a世a ", null, "a世a");
    assertStringTrim("UNICODE", "asd", "x", "asd");
    assertStringTrim("UNICODE", "xxasdxx", "x", "asd");
    assertStringTrim("UNICODE", "xa世ax", "x", "a世a");
    assertStringTrim("UNICODE_CI", "", "", "");
    assertStringTrim("UNICODE_CI", "", "xyz", "");
    assertStringTrim("UNICODE_CI", "asd", "", "asd");
    assertStringTrim("UNICODE_CI", "asd", null, "asd");
    assertStringTrim("UNICODE_CI", "  asd  ", null, "asd");
    assertStringTrim("UNICODE_CI", " a世a ", null, "a世a");
    assertStringTrim("UNICODE_CI", "asd", "x", "asd");
    assertStringTrim("UNICODE_CI", "xxasdxx", "x", "asd");
    assertStringTrim("UNICODE_CI", "xa世ax", "x", "a世a");
    // Case variation.
    assertStringTrim("UTF8_BINARY", "asd", "A", "asd");
    assertStringTrim("UTF8_BINARY", "ddsXXXaa", "asd", "XXX");
    assertStringTrim("UTF8_BINARY", "ASD", "a", "ASD");
    assertStringTrim("UTF8_LCASE", "asd", "A", "sd");
    assertStringTrim("UTF8_LCASE", "ASD", "a", "SD");
    assertStringTrim("UTF8_LCASE", "ddsXXXaa", "ASD", "XXX");
    assertStringTrim("UNICODE", "asd", "A", "asd");
    assertStringTrim("UNICODE", "ASD", "a", "ASD");
    assertStringTrim("UNICODE", "ddsXXXaa", "asd", "XXX");
    assertStringTrim("UNICODE_CI", "asd", "A", "sd");
    assertStringTrim("UNICODE_CI", "ASD", "a", "SD");
    assertStringTrim("UNICODE_CI", "ddsXXXaa", "ASD", "XXX");
    // One-to-many case mapping (e.g. Turkish dotted I)..
    assertStringTrim("UTF8_BINARY", "ẞaaaẞ", "ß", "ẞaaaẞ");
    assertStringTrim("UTF8_BINARY", "ßaaaß", "ẞ", "ßaaaß");
    assertStringTrim("UTF8_BINARY", "Ëaaaẞ", "Ëẞ", "aaa");
    assertStringTrim("UTF8_LCASE", "ẞaaaẞ", "ß", "aaa");
    assertStringTrim("UTF8_LCASE", "ßaaaß", "ẞ", "aaa");
    assertStringTrim("UTF8_LCASE", "Ëaaaẞ", "Ëẞ", "aaa");
    assertStringTrim("UNICODE", "ẞaaaẞ", "ß", "ẞaaaẞ");
    assertStringTrim("UNICODE", "ßaaaß", "ẞ", "ßaaaß");
    assertStringTrim("UNICODE", "Ëaaaẞ", "Ëẞ", "aaa");
    assertStringTrim("UNICODE_CI", "ẞaaaẞ", "ß", "aaa");
    assertStringTrim("UNICODE_CI", "ßaaaß", "ẞ", "aaa");
    assertStringTrim("UNICODE_CI", "Ëaaaẞ", "Ëẞ", "aaa");
    // One-to-many case mapping (e.g. Turkish dotted I).
    assertStringTrim("UTF8_BINARY", "i", "i", "");
    assertStringTrim("UTF8_BINARY", "iii", "I", "iii");
    assertStringTrim("UTF8_BINARY", "I", "iii", "I");
    assertStringTrim("UTF8_BINARY", "ixi", "i", "x");
    assertStringTrim("UTF8_BINARY", "i", "İ", "i");
    assertStringTrim("UTF8_BINARY", "i\u0307", "İ", "i\u0307");
    assertStringTrim("UTF8_BINARY", "i\u0307", "i", "\u0307");
    assertStringTrim("UTF8_BINARY", "i\u0307", "\u0307", "i");
    assertStringTrim("UTF8_BINARY", "i\u0307", "i\u0307", "");
    assertStringTrim("UTF8_BINARY", "i\u0307i\u0307", "i\u0307", "");
    assertStringTrim("UTF8_BINARY", "i\u0307\u0307", "i\u0307", "");
    assertStringTrim("UTF8_BINARY", "i\u0307i", "i\u0307", "");
    assertStringTrim("UTF8_BINARY", "i\u0307i", "İ", "i\u0307i");
    assertStringTrim("UTF8_BINARY", "i\u0307İ", "i\u0307", "İ");
    assertStringTrim("UTF8_BINARY", "i\u0307İ", "İ", "i\u0307");
    assertStringTrim("UTF8_BINARY", "İ", "İ", "");
    assertStringTrim("UTF8_BINARY", "IXi", "İ", "IXi");
    assertStringTrim("UTF8_BINARY", "ix\u0307", "Ixİ", "ix\u0307");
    assertStringTrim("UTF8_BINARY", "i\u0307x", "IXİ", "i\u0307x");
    assertStringTrim("UTF8_BINARY", "i\u0307x", "ix\u0307İ", "");
    assertStringTrim("UTF8_BINARY", "İ", "i", "İ");
    assertStringTrim("UTF8_BINARY", "İ", "\u0307", "İ");
    assertStringTrim("UTF8_BINARY", "Ixİ", "i\u0307", "Ixİ");
    assertStringTrim("UTF8_BINARY", "IXİ", "ix\u0307", "IXİ");
    assertStringTrim("UTF8_BINARY", "xi\u0307", "\u0307IX", "xi");
    assertStringTrim("UTF8_LCASE", "i", "i", "");
    assertStringTrim("UTF8_LCASE", "iii", "I", "");
    assertStringTrim("UTF8_LCASE", "I", "iii", "");
    assertStringTrim("UTF8_LCASE", "ixi", "i", "x");
    assertStringTrim("UTF8_LCASE", "i", "İ", "i");
    assertStringTrim("UTF8_LCASE", "i\u0307", "İ", "");
    assertStringTrim("UTF8_LCASE", "i\u0307", "i", "\u0307");
    assertStringTrim("UTF8_LCASE", "i\u0307", "\u0307", "i");
    assertStringTrim("UTF8_LCASE", "i\u0307", "i\u0307", "");
    assertStringTrim("UTF8_LCASE", "i\u0307i\u0307", "i\u0307", "");
    assertStringTrim("UTF8_LCASE", "i\u0307\u0307", "i\u0307", "");
    assertStringTrim("UTF8_LCASE", "i\u0307i", "i\u0307", "");
    assertStringTrim("UTF8_LCASE", "i\u0307i", "İ", "i");
    assertStringTrim("UTF8_LCASE", "i\u0307İ", "i\u0307", "İ");
    assertStringTrim("UTF8_LCASE", "i\u0307İ", "İ", "");
    assertStringTrim("UTF8_LCASE", "İ", "İ", "");
    assertStringTrim("UTF8_LCASE", "IXi", "İ", "IXi");
    assertStringTrim("UTF8_LCASE", "ix\u0307", "Ixİ", "\u0307");
    assertStringTrim("UTF8_LCASE", "i\u0307x", "IXİ", "");
    assertStringTrim("UTF8_LCASE", "i\u0307x", "I\u0307xİ", "");
    assertStringTrim("UTF8_LCASE", "İ", "i", "İ");
    assertStringTrim("UTF8_LCASE", "İ", "\u0307", "İ");
    assertStringTrim("UTF8_LCASE", "Ixİ", "i\u0307", "xİ");
    assertStringTrim("UTF8_LCASE", "IXİ", "ix\u0307", "İ");
    assertStringTrim("UTF8_LCASE", "xi\u0307", "\u0307IX", "");
    assertStringTrim("UNICODE", "i", "i", "");
    assertStringTrim("UNICODE", "iii", "I", "iii");
    assertStringTrim("UNICODE", "I", "iii", "I");
    assertStringTrim("UNICODE", "ixi", "i", "x");
    assertStringTrim("UNICODE", "i", "İ", "i");
    assertStringTrim("UNICODE", "i\u0307", "İ", "i\u0307");
    assertStringTrim("UNICODE", "i\u0307", "i", "i\u0307");
    assertStringTrim("UNICODE", "i\u0307", "\u0307", "i\u0307");
    assertStringTrim("UNICODE", "i\u0307", "i\u0307", "i\u0307");
    assertStringTrim("UNICODE", "i\u0307i\u0307", "i\u0307", "i\u0307i\u0307");
    assertStringTrim("UNICODE", "i\u0307\u0307", "i\u0307", "i\u0307\u0307");
    assertStringTrim("UNICODE", "i\u0307i", "i\u0307", "i\u0307");
    assertStringTrim("UNICODE", "i\u0307i", "İ", "i\u0307i");
    assertStringTrim("UNICODE", "i\u0307İ", "i\u0307", "i\u0307İ");
    assertStringTrim("UNICODE", "i\u0307İ", "İ", "i\u0307");
    assertStringTrim("UNICODE", "İ", "İ", "");
    assertStringTrim("UNICODE", "IXi", "İ", "IXi");
    assertStringTrim("UNICODE", "ix\u0307", "Ixİ", "ix\u0307");
    assertStringTrim("UNICODE", "i\u0307x", "IXİ", "i\u0307x");
    assertStringTrim("UNICODE", "i\u0307x", "ix\u0307İ", "i\u0307");
    assertStringTrim("UNICODE", "İ", "i", "İ");
    assertStringTrim("UNICODE", "İ", "\u0307", "İ");
    assertStringTrim("UNICODE", "i\u0307", "i\u0307", "i\u0307");
    assertStringTrim("UNICODE", "Ixİ", "i\u0307", "Ixİ");
    assertStringTrim("UNICODE", "IXİ", "ix\u0307", "IXİ");
    assertStringTrim("UNICODE", "xi\u0307", "\u0307IX", "xi\u0307");
    assertStringTrim("UNICODE_CI", "i", "i", "");
    assertStringTrim("UNICODE_CI", "iii", "I", "");
    assertStringTrim("UNICODE_CI", "I", "iii", "");
    assertStringTrim("UNICODE_CI", "ixi", "i", "x");
    assertStringTrim("UNICODE_CI", "i", "İ", "i");
    assertStringTrim("UNICODE_CI", "i\u0307", "İ", "");
    assertStringTrim("UNICODE_CI", "i\u0307", "i", "i\u0307");
    assertStringTrim("UNICODE_CI", "i\u0307", "\u0307", "i\u0307");
    assertStringTrim("UNICODE_CI", "i\u0307", "i\u0307", "i\u0307");
    assertStringTrim("UNICODE_CI", "i\u0307i\u0307", "i\u0307", "i\u0307i\u0307");
    assertStringTrim("UNICODE_CI", "i\u0307\u0307", "i\u0307", "i\u0307\u0307");
    assertStringTrim("UNICODE_CI", "i\u0307i", "i\u0307", "i\u0307");
    assertStringTrim("UNICODE_CI", "i\u0307i", "İ", "i");
    assertStringTrim("UNICODE_CI", "i\u0307İ", "i\u0307", "i\u0307İ");
    assertStringTrim("UNICODE_CI", "i\u0307İ", "İ", "");
    assertStringTrim("UNICODE_CI", "İ", "İ", "");
    assertStringTrim("UNICODE_CI", "IXi", "İ", "IXi");
    assertStringTrim("UNICODE_CI", "ix\u0307", "Ixİ", "x\u0307");
    assertStringTrim("UNICODE_CI", "i\u0307x", "IXİ", "");
    assertStringTrim("UNICODE_CI", "i\u0307x", "I\u0307xİ", "");
    assertStringTrim("UNICODE_CI", "İ", "i", "İ");
    assertStringTrim("UNICODE_CI", "İ", "\u0307", "İ");
    assertStringTrim("UNICODE_CI", "i\u0307", "i\u0307", "i\u0307");
    assertStringTrim("UNICODE_CI", "Ixİ", "i\u0307", "xİ");
    assertStringTrim("UNICODE_CI", "IXİ", "ix\u0307", "İ");
    assertStringTrim("UNICODE_CI", "xi\u0307", "\u0307IX", "i\u0307");
    // Conditional case mapping (e.g. Greek sigmas).
    assertStringTrim("UTF8_BINARY", "ςxς", "σ", "ςxς");
    assertStringTrim("UTF8_BINARY", "ςxς", "ς", "x");
    assertStringTrim("UTF8_BINARY", "ςxς", "Σ", "ςxς");
    assertStringTrim("UTF8_BINARY", "σxσ", "σ", "x");
    assertStringTrim("UTF8_BINARY", "σxσ", "ς", "σxσ");
    assertStringTrim("UTF8_BINARY", "σxσ", "Σ", "σxσ");
    assertStringTrim("UTF8_BINARY", "ΣxΣ", "σ", "ΣxΣ");
    assertStringTrim("UTF8_BINARY", "ΣxΣ", "ς", "ΣxΣ");
    assertStringTrim("UTF8_BINARY", "ΣxΣ", "Σ", "x");
    assertStringTrim("UTF8_LCASE", "ςxς", "σ", "x");
    assertStringTrim("UTF8_LCASE", "ςxς", "ς", "x");
    assertStringTrim("UTF8_LCASE", "ςxς", "Σ", "x");
    assertStringTrim("UTF8_LCASE", "σxσ", "σ", "x");
    assertStringTrim("UTF8_LCASE", "σxσ", "ς", "x");
    assertStringTrim("UTF8_LCASE", "σxσ", "Σ", "x");
    assertStringTrim("UTF8_LCASE", "ΣxΣ", "σ", "x");
    assertStringTrim("UTF8_LCASE", "ΣxΣ", "ς", "x");
    assertStringTrim("UTF8_LCASE", "ΣxΣ", "Σ", "x");
    assertStringTrim("UNICODE", "ςxς", "σ", "ςxς");
    assertStringTrim("UNICODE", "ςxς", "ς", "x");
    assertStringTrim("UNICODE", "ςxς", "Σ", "ςxς");
    assertStringTrim("UNICODE", "σxσ", "σ", "x");
    assertStringTrim("UNICODE", "σxσ", "ς", "σxσ");
    assertStringTrim("UNICODE", "σxσ", "Σ", "σxσ");
    assertStringTrim("UNICODE", "ΣxΣ", "σ", "ΣxΣ");
    assertStringTrim("UNICODE", "ΣxΣ", "ς", "ΣxΣ");
    assertStringTrim("UNICODE", "ΣxΣ", "Σ", "x");
    assertStringTrim("UNICODE_CI", "ςxς", "σ", "x");
    assertStringTrim("UNICODE_CI", "ςxς", "ς", "x");
    assertStringTrim("UNICODE_CI", "ςxς", "Σ", "x");
    assertStringTrim("UNICODE_CI", "σxσ", "σ", "x");
    assertStringTrim("UNICODE_CI", "σxσ", "ς", "x");
    assertStringTrim("UNICODE_CI", "σxσ", "Σ", "x");
    assertStringTrim("UNICODE_CI", "ΣxΣ", "σ", "x");
    assertStringTrim("UNICODE_CI", "ΣxΣ", "ς", "x");
    assertStringTrim("UNICODE_CI", "ΣxΣ", "Σ", "x");
    // Unicode normalization.
    assertStringTrim("UTF8_BINARY", "åβγδa\u030A", "å", "βγδa\u030A");
    assertStringTrim("UTF8_LCASE", "åβγδa\u030A", "Å", "βγδa\u030A");
    assertStringTrim("UNICODE", "åβγδa\u030A", "å", "βγδ");
    assertStringTrim("UNICODE_CI", "åβγδa\u030A", "Å", "βγδ");
    // Surrogate pairs.
    assertStringTrim("UTF8_BINARY", "a🙃b🙃c", "🙃", "a🙃b🙃c");
    assertStringTrim("UTF8_LCASE", "a🙃b🙃c", "🙃", "a🙃b🙃c");
    assertStringTrim("UNICODE", "a🙃b🙃c", "🙃", "a🙃b🙃c");
    assertStringTrim("UNICODE_CI", "a🙃b🙃c", "🙃", "a🙃b🙃c");
    assertStringTrim("UTF8_BINARY", "a🙃b🙃c", "ac", "🙃b🙃");
    assertStringTrim("UTF8_LCASE", "a🙃b🙃c", "ac", "🙃b🙃");
    assertStringTrim("UNICODE", "a🙃b🙃c", "ac", "🙃b🙃");
    assertStringTrim("UNICODE_CI", "a🙃b🙃c", "ac", "🙃b🙃");
    assertStringTrim("UTF8_BINARY", "a🙃b🙃c", "a🙃c", "b");
    assertStringTrim("UTF8_LCASE", "a🙃b🙃c", "a🙃c", "b");
    assertStringTrim("UNICODE", "a🙃b🙃c", "a🙃c", "b");
    assertStringTrim("UNICODE_CI", "a🙃b🙃c", "a🙃c", "b");
    assertStringTrim("UTF8_BINARY", "a🙃b🙃c", "abc🙃", "");
    assertStringTrim("UTF8_LCASE", "a🙃b🙃c", "abc🙃", "");
    assertStringTrim("UNICODE", "a🙃b🙃c", "abc🙃", "");
    assertStringTrim("UNICODE_CI", "a🙃b🙃c", "abc🙃", "");
    assertStringTrim("UTF8_BINARY", "😀😆😃😄", "😀😄", "😆😃");
    assertStringTrim("UTF8_LCASE", "😀😆😃😄", "😀😄", "😆😃");
    assertStringTrim("UNICODE", "😀😆😃😄", "😀😄", "😆😃");
    assertStringTrim("UNICODE_CI", "😀😆😃😄", "😀😄", "😆😃");
    assertStringTrim("UTF8_BINARY", "😀😆😃😄", "😃😄", "😀😆");
    assertStringTrim("UTF8_LCASE", "😀😆😃😄", "😃😄", "😀😆");
    assertStringTrim("UNICODE", "😀😆😃😄", "😃😄", "😀😆");
    assertStringTrim("UNICODE_CI", "😀😆😃😄", "😃😄", "😀😆");
    assertStringTrim("UTF8_BINARY", "😀😆😃😄", "😀😆😃😄", "");
    assertStringTrim("UTF8_LCASE", "😀😆😃😄", "😀😆😃😄", "");
    assertStringTrim("UNICODE", "😀😆😃😄", "😀😆😃😄", "");
    assertStringTrim("UNICODE_CI", "😀😆😃😄", "😀😆😃😄", "");
    assertStringTrim("UTF8_BINARY", "𐐅", "𐐅", "");
    assertStringTrim("UTF8_LCASE", "𐐅", "𐐅", "");
    assertStringTrim("UNICODE", "𐐅", "𐐅", "");
    assertStringTrim("UNICODE_CI", "𐐅", "𐐅", "");
    assertStringTrim("UTF8_BINARY", "𐐅", "𐐭", "𐐅");
    assertStringTrim("UTF8_LCASE", "𐐅", "𐐭", "");
    assertStringTrim("UNICODE", "𐐅", "𐐭", "𐐅");
    assertStringTrim("UNICODE_CI", "𐐅", "𐐭", "");
    assertStringTrim("UTF8_BINARY", "𝔸", "𝔸", "");
    assertStringTrim("UTF8_LCASE", "𝔸", "𝔸", "");
    assertStringTrim("UNICODE", "𝔸", "𝔸", "");
    assertStringTrim("UNICODE_CI", "𝔸", "𝔸", "");
    assertStringTrim("UTF8_BINARY", "𝔸", "A", "𝔸");
    assertStringTrim("UTF8_LCASE", "𝔸", "A", "𝔸");
    assertStringTrim("UNICODE", "𝔸", "A", "𝔸");
    assertStringTrim("UNICODE_CI", "𝔸", "A", "");
    assertStringTrim("UTF8_BINARY", "𝔸", "a", "𝔸");
    assertStringTrim("UTF8_LCASE", "𝔸", "a", "𝔸");
    assertStringTrim("UNICODE", "𝔸", "a", "𝔸");
    assertStringTrim("UNICODE_CI", "𝔸", "a", "");
  }

  /**
   * Verify the behaviour of the `StringTrimLeft` collation support class.
   */

  private void assertStringTrimLeft(String collationName, String sourceString, String trimString,
      String expected) throws SparkException {
    // Prepare the input and expected result.
    int collationId = CollationFactory.collationNameToId(collationName);
    UTF8String src = UTF8String.fromString(sourceString);
    UTF8String trim = UTF8String.fromString(trimString);
    UTF8String result;

    if (trimString == null) {
      // Trim string is ASCII space.
      result = CollationSupport.StringTrimLeft.exec(src);
    } else {
      // Trim string is specified.
      result = CollationSupport.StringTrimLeft.exec(src, trim, collationId);
    }

    // Test that StringTrimLeft result is as expected.
    assertEquals(UTF8String.fromString(expected), result);
  }

  @Test
  public void testStringTrimLeft() throws SparkException {
    // Basic tests - UTF8_BINARY.
    assertStringTrimLeft("UTF8_BINARY", "", "", "");
    assertStringTrimLeft("UTF8_BINARY", "", "xyz", "");
    assertStringTrimLeft("UTF8_BINARY", "asd", "", "asd");
    assertStringTrimLeft("UTF8_BINARY", "asd", null, "asd");
    assertStringTrimLeft("UTF8_BINARY", "  asd  ", null, "asd  ");
    assertStringTrimLeft("UTF8_BINARY", " a世a ", null, "a世a ");
    assertStringTrimLeft("UTF8_BINARY", "asd", "x", "asd");
    assertStringTrimLeft("UTF8_BINARY", "xxasdxx", "x", "asdxx");
    assertStringTrimLeft("UTF8_BINARY", "xa世ax", "x", "a世ax");
    // Basic tests - UTF8_LCASE.
    assertStringTrimLeft("UTF8_LCASE", "", "", "");
    assertStringTrimLeft("UTF8_LCASE", "", "xyz", "");
    assertStringTrimLeft("UTF8_LCASE", "asd", "", "asd");
    assertStringTrimLeft("UTF8_LCASE", "asd", null, "asd");
    assertStringTrimLeft("UTF8_LCASE", "  asd  ", null, "asd  ");
    assertStringTrimLeft("UTF8_LCASE", " a世a ", null, "a世a ");
    assertStringTrimLeft("UTF8_LCASE", "asd", "x", "asd");
    assertStringTrimLeft("UTF8_LCASE", "xxasdxx", "x", "asdxx");
    assertStringTrimLeft("UTF8_LCASE", "xa世ax", "x", "a世ax");
    // Basic tests - UNICODE.
    assertStringTrimLeft("UNICODE", "", "", "");
    assertStringTrimLeft("UNICODE", "", "xyz", "");
    assertStringTrimLeft("UNICODE", "asd", "", "asd");
    assertStringTrimLeft("UNICODE", "asd", null, "asd");
    assertStringTrimLeft("UNICODE", "  asd  ", null, "asd  ");
    assertStringTrimLeft("UNICODE", " a世a ", null, "a世a ");
    assertStringTrimLeft("UNICODE", "asd", "x", "asd");
    assertStringTrimLeft("UNICODE", "xxasdxx", "x", "asdxx");
    assertStringTrimLeft("UNICODE", "xa世ax", "x", "a世ax");
    // Basic tests - UNICODE_CI.
    assertStringTrimLeft("UNICODE_CI", "", "", "");
    assertStringTrimLeft("UNICODE_CI", "", "xyz", "");
    assertStringTrimLeft("UNICODE_CI", "asd", "", "asd");
    assertStringTrimLeft("UNICODE_CI", "asd", null, "asd");
    assertStringTrimLeft("UNICODE_CI", "  asd  ", null, "asd  ");
    assertStringTrimLeft("UNICODE_CI", " a世a ", null, "a世a ");
    assertStringTrimLeft("UNICODE_CI", "asd", "x", "asd");
    assertStringTrimLeft("UNICODE_CI", "xxasdxx", "x", "asdxx");
    assertStringTrimLeft("UNICODE_CI", "xa世ax", "x", "a世ax");
    // Case variation.
    assertStringTrimLeft("UTF8_BINARY", "ddsXXXaa", "asd", "XXXaa");
    assertStringTrimLeft("UTF8_LCASE", "ddsXXXaa", "aSd", "XXXaa");
    assertStringTrimLeft("UNICODE", "ddsXXXaa", "asd", "XXXaa");
    assertStringTrimLeft("UNICODE_CI", "ddsXXXaa", "aSd", "XXXaa");
    // One-to-many case mapping (e.g. Turkish dotted I)..
    assertStringTrimLeft("UTF8_BINARY", "ẞaaaẞ", "ß", "ẞaaaẞ");
    assertStringTrimLeft("UTF8_BINARY", "ßaaaß", "ẞ", "ßaaaß");
    assertStringTrimLeft("UTF8_BINARY", "Ëaaaẞ", "Ëẞ", "aaaẞ");
    assertStringTrimLeft("UTF8_LCASE", "ẞaaaẞ", "ß", "aaaẞ");
    assertStringTrimLeft("UTF8_LCASE", "ßaaaß", "ẞ", "aaaß");
    assertStringTrimLeft("UTF8_LCASE", "Ëaaaẞ", "Ëẞ", "aaaẞ");
    assertStringTrimLeft("UNICODE", "ẞaaaẞ", "ß", "ẞaaaẞ");
    assertStringTrimLeft("UNICODE", "ßaaaß", "ẞ", "ßaaaß");
    assertStringTrimLeft("UNICODE", "Ëaaaẞ", "Ëẞ", "aaaẞ");
    assertStringTrimLeft("UNICODE_CI", "ẞaaaẞ", "ß", "aaaẞ");
    assertStringTrimLeft("UNICODE_CI", "ßaaaß", "ẞ", "aaaß");
    assertStringTrimLeft("UNICODE_CI", "Ëaaaẞ", "Ëẞ", "aaaẞ");
    // One-to-many case mapping (e.g. Turkish dotted I).
    assertStringTrimLeft("UTF8_BINARY", "i", "i", "");
    assertStringTrimLeft("UTF8_BINARY", "iii", "I", "iii");
    assertStringTrimLeft("UTF8_BINARY", "I", "iii", "I");
    assertStringTrimLeft("UTF8_BINARY", "ixi", "i", "xi");
    assertStringTrimLeft("UTF8_BINARY", "i", "İ", "i");
    assertStringTrimLeft("UTF8_BINARY", "i\u0307", "İ", "i\u0307");
    assertStringTrimLeft("UTF8_BINARY", "i\u0307", "i", "\u0307");
    assertStringTrimLeft("UTF8_BINARY", "i\u0307", "\u0307", "i\u0307");
    assertStringTrimLeft("UTF8_BINARY", "i\u0307", "i\u0307", "");
    assertStringTrimLeft("UTF8_BINARY", "i\u0307i\u0307", "i\u0307", "");
    assertStringTrimLeft("UTF8_BINARY", "i\u0307\u0307", "i\u0307", "");
    assertStringTrimLeft("UTF8_BINARY", "i\u0307i", "i\u0307", "");
    assertStringTrimLeft("UTF8_BINARY", "i\u0307i", "İ", "i\u0307i");
    assertStringTrimLeft("UTF8_BINARY", "i\u0307İ", "i\u0307", "İ");
    assertStringTrimLeft("UTF8_BINARY", "i\u0307İ", "İ", "i\u0307İ");
    assertStringTrimLeft("UTF8_BINARY", "İ", "İ", "");
    assertStringTrimLeft("UTF8_BINARY", "IXi", "İ", "IXi");
    assertStringTrimLeft("UTF8_BINARY", "ix\u0307", "Ixİ", "ix\u0307");
    assertStringTrimLeft("UTF8_BINARY", "i\u0307x", "IXİ", "i\u0307x");
    assertStringTrimLeft("UTF8_BINARY", "i\u0307x", "ix\u0307İ", "");
    assertStringTrimLeft("UTF8_BINARY", "İ", "i", "İ");
    assertStringTrimLeft("UTF8_BINARY", "İ", "\u0307", "İ");
    assertStringTrimLeft("UTF8_BINARY", "Ixİ", "i\u0307", "Ixİ");
    assertStringTrimLeft("UTF8_BINARY", "IXİ", "ix\u0307", "IXİ");
    assertStringTrimLeft("UTF8_BINARY", "xi\u0307", "\u0307IX", "xi\u0307");
    assertStringTrimLeft("UTF8_LCASE", "i", "i", "");
    assertStringTrimLeft("UTF8_LCASE", "iii", "I", "");
    assertStringTrimLeft("UTF8_LCASE", "I", "iii", "");
    assertStringTrimLeft("UTF8_LCASE", "ixi", "i", "xi");
    assertStringTrimLeft("UTF8_LCASE", "i", "İ", "i");
    assertStringTrimLeft("UTF8_LCASE", "i\u0307", "İ", "");
    assertStringTrimLeft("UTF8_LCASE", "i\u0307", "i", "\u0307");
    assertStringTrimLeft("UTF8_LCASE", "i\u0307", "\u0307", "i\u0307");
    assertStringTrimLeft("UTF8_LCASE", "i\u0307", "i\u0307", "");
    assertStringTrimLeft("UTF8_LCASE", "i\u0307i\u0307", "i\u0307", "");
    assertStringTrimLeft("UTF8_LCASE", "i\u0307\u0307", "i\u0307", "");
    assertStringTrimLeft("UTF8_LCASE", "i\u0307i", "i\u0307", "");
    assertStringTrimLeft("UTF8_LCASE", "i\u0307i", "İ", "i");
    assertStringTrimLeft("UTF8_LCASE", "i\u0307İ", "i\u0307", "İ");
    assertStringTrimLeft("UTF8_LCASE", "i\u0307İ", "İ", "");
    assertStringTrimLeft("UTF8_LCASE", "İ", "İ", "");
    assertStringTrimLeft("UTF8_LCASE", "IXi", "İ", "IXi");
    assertStringTrimLeft("UTF8_LCASE", "ix\u0307", "Ixİ", "\u0307");
    assertStringTrimLeft("UTF8_LCASE", "i\u0307x", "IXİ", "");
    assertStringTrimLeft("UTF8_LCASE", "i\u0307x", "I\u0307xİ", "");
    assertStringTrimLeft("UTF8_LCASE", "İ", "i", "İ");
    assertStringTrimLeft("UTF8_LCASE", "İ", "\u0307", "İ");
    assertStringTrimLeft("UTF8_LCASE", "Ixİ", "i\u0307", "xİ");
    assertStringTrimLeft("UTF8_LCASE", "IXİ", "ix\u0307", "İ");
    assertStringTrimLeft("UTF8_LCASE", "xi\u0307", "\u0307IX", "");
    assertStringTrimLeft("UNICODE", "i", "i", "");
    assertStringTrimLeft("UNICODE", "iii", "I", "iii");
    assertStringTrimLeft("UNICODE", "I", "iii", "I");
    assertStringTrimLeft("UNICODE", "ixi", "i", "xi");
    assertStringTrimLeft("UNICODE", "i", "İ", "i");
    assertStringTrimLeft("UNICODE", "i\u0307", "İ", "i\u0307");
    assertStringTrimLeft("UNICODE", "i\u0307", "i", "i\u0307");
    assertStringTrimLeft("UNICODE", "i\u0307", "\u0307", "i\u0307");
    assertStringTrimLeft("UNICODE", "i\u0307", "i\u0307", "i\u0307");
    assertStringTrimLeft("UNICODE", "i\u0307i\u0307", "i\u0307", "i\u0307i\u0307");
    assertStringTrimLeft("UNICODE", "i\u0307\u0307", "i\u0307", "i\u0307\u0307");
    assertStringTrimLeft("UNICODE", "i\u0307i", "i\u0307", "i\u0307i");
    assertStringTrimLeft("UNICODE", "i\u0307i", "İ", "i\u0307i");
    assertStringTrimLeft("UNICODE", "i\u0307İ", "i\u0307", "i\u0307İ");
    assertStringTrimLeft("UNICODE", "i\u0307İ", "İ", "i\u0307İ");
    assertStringTrimLeft("UNICODE", "İ", "İ", "");
    assertStringTrimLeft("UNICODE", "IXi", "İ", "IXi");
    assertStringTrimLeft("UNICODE", "ix\u0307", "Ixİ", "ix\u0307");
    assertStringTrimLeft("UNICODE", "i\u0307x", "IXİ", "i\u0307x");
    assertStringTrimLeft("UNICODE", "i\u0307x", "ix\u0307İ", "i\u0307x");
    assertStringTrimLeft("UNICODE", "İ", "i", "İ");
    assertStringTrimLeft("UNICODE", "İ", "\u0307", "İ");
    assertStringTrimLeft("UNICODE", "i\u0307", "i\u0307", "i\u0307");
    assertStringTrimLeft("UNICODE", "Ixİ", "i\u0307", "Ixİ");
    assertStringTrimLeft("UNICODE", "IXİ", "ix\u0307", "IXİ");
    assertStringTrimLeft("UNICODE", "xi\u0307", "\u0307IX", "xi\u0307");
    assertStringTrimLeft("UNICODE_CI", "i", "i", "");
    assertStringTrimLeft("UNICODE_CI", "iii", "I", "");
    assertStringTrimLeft("UNICODE_CI", "I", "iii", "");
    assertStringTrimLeft("UNICODE_CI", "ixi", "i", "xi");
    assertStringTrimLeft("UNICODE_CI", "i", "İ", "i");
    assertStringTrimLeft("UNICODE_CI", "i\u0307", "İ", "");
    assertStringTrimLeft("UNICODE_CI", "i\u0307", "i", "i\u0307");
    assertStringTrimLeft("UNICODE_CI", "i\u0307", "\u0307", "i\u0307");
    assertStringTrimLeft("UNICODE_CI", "i\u0307", "i\u0307", "i\u0307");
    assertStringTrimLeft("UNICODE_CI", "i\u0307i\u0307", "i\u0307", "i\u0307i\u0307");
    assertStringTrimLeft("UNICODE_CI", "i\u0307\u0307", "i\u0307", "i\u0307\u0307");
    assertStringTrimLeft("UNICODE_CI", "i\u0307i", "i\u0307", "i\u0307i");
    assertStringTrimLeft("UNICODE_CI", "i\u0307i", "İ", "i");
    assertStringTrimLeft("UNICODE_CI", "i\u0307İ", "i\u0307", "i\u0307İ");
    assertStringTrimLeft("UNICODE_CI", "i\u0307İ", "İ", "");
    assertStringTrimLeft("UNICODE_CI", "İ", "İ", "");
    assertStringTrimLeft("UNICODE_CI", "IXi", "İ", "IXi");
    assertStringTrimLeft("UNICODE_CI", "ix\u0307", "Ixİ", "x\u0307");
    assertStringTrimLeft("UNICODE_CI", "i\u0307x", "IXİ", "");
    assertStringTrimLeft("UNICODE_CI", "i\u0307x", "I\u0307xİ", "");
    assertStringTrimLeft("UNICODE_CI", "İ", "i", "İ");
    assertStringTrimLeft("UNICODE_CI", "İ", "\u0307", "İ");
    assertStringTrimLeft("UNICODE_CI", "i\u0307", "i\u0307", "i\u0307");
    assertStringTrimLeft("UNICODE_CI", "Ixİ", "i\u0307", "xİ");
    assertStringTrimLeft("UNICODE_CI", "IXİ", "ix\u0307", "İ");
    assertStringTrimLeft("UNICODE_CI", "xi\u0307", "\u0307IX", "i\u0307");
    // Conditional case mapping (e.g. Greek sigmas).
    assertStringTrimLeft("UTF8_BINARY", "ςxς", "σ", "ςxς");
    assertStringTrimLeft("UTF8_BINARY", "ςxς", "ς", "xς");
    assertStringTrimLeft("UTF8_BINARY", "ςxς", "Σ", "ςxς");
    assertStringTrimLeft("UTF8_BINARY", "σxσ", "σ", "xσ");
    assertStringTrimLeft("UTF8_BINARY", "σxσ", "ς", "σxσ");
    assertStringTrimLeft("UTF8_BINARY", "σxσ", "Σ", "σxσ");
    assertStringTrimLeft("UTF8_BINARY", "ΣxΣ", "σ", "ΣxΣ");
    assertStringTrimLeft("UTF8_BINARY", "ΣxΣ", "ς", "ΣxΣ");
    assertStringTrimLeft("UTF8_BINARY", "ΣxΣ", "Σ", "xΣ");
    assertStringTrimLeft("UTF8_LCASE", "ςxς", "σ", "xς");
    assertStringTrimLeft("UTF8_LCASE", "ςxς", "ς", "xς");
    assertStringTrimLeft("UTF8_LCASE", "ςxς", "Σ", "xς");
    assertStringTrimLeft("UTF8_LCASE", "σxσ", "σ", "xσ");
    assertStringTrimLeft("UTF8_LCASE", "σxσ", "ς", "xσ");
    assertStringTrimLeft("UTF8_LCASE", "σxσ", "Σ", "xσ");
    assertStringTrimLeft("UTF8_LCASE", "ΣxΣ", "σ", "xΣ");
    assertStringTrimLeft("UTF8_LCASE", "ΣxΣ", "ς", "xΣ");
    assertStringTrimLeft("UTF8_LCASE", "ΣxΣ", "Σ", "xΣ");
    assertStringTrimLeft("UNICODE", "ςxς", "σ", "ςxς");
    assertStringTrimLeft("UNICODE", "ςxς", "ς", "xς");
    assertStringTrimLeft("UNICODE", "ςxς", "Σ", "ςxς");
    assertStringTrimLeft("UNICODE", "σxσ", "σ", "xσ");
    assertStringTrimLeft("UNICODE", "σxσ", "ς", "σxσ");
    assertStringTrimLeft("UNICODE", "σxσ", "Σ", "σxσ");
    assertStringTrimLeft("UNICODE", "ΣxΣ", "σ", "ΣxΣ");
    assertStringTrimLeft("UNICODE", "ΣxΣ", "ς", "ΣxΣ");
    assertStringTrimLeft("UNICODE", "ΣxΣ", "Σ", "xΣ");
    assertStringTrimLeft("UNICODE_CI", "ςxς", "σ", "xς");
    assertStringTrimLeft("UNICODE_CI", "ςxς", "ς", "xς");
    assertStringTrimLeft("UNICODE_CI", "ςxς", "Σ", "xς");
    assertStringTrimLeft("UNICODE_CI", "σxσ", "σ", "xσ");
    assertStringTrimLeft("UNICODE_CI", "σxσ", "ς", "xσ");
    assertStringTrimLeft("UNICODE_CI", "σxσ", "Σ", "xσ");
    assertStringTrimLeft("UNICODE_CI", "ΣxΣ", "σ", "xΣ");
    assertStringTrimLeft("UNICODE_CI", "ΣxΣ", "ς", "xΣ");
    assertStringTrimLeft("UNICODE_CI", "ΣxΣ", "Σ", "xΣ");
    // Unicode normalization.
    assertStringTrimLeft("UTF8_BINARY", "åβγδa\u030A", "å", "βγδa\u030A");
    assertStringTrimLeft("UTF8_LCASE", "åβγδa\u030A", "Å", "βγδa\u030A");
    assertStringTrimLeft("UNICODE", "åβγδa\u030A", "å", "βγδa\u030A");
    assertStringTrimLeft("UNICODE_CI", "åβγδa\u030A", "Å", "βγδa\u030A");
    // Surrogate pairs.
    assertStringTrimLeft("UTF8_BINARY", "a🙃b🙃c", "🙃", "a🙃b🙃c");
    assertStringTrimLeft("UTF8_LCASE", "a🙃b🙃c", "🙃", "a🙃b🙃c");
    assertStringTrimLeft("UNICODE", "a🙃b🙃c", "🙃", "a🙃b🙃c");
    assertStringTrimLeft("UNICODE_CI", "a🙃b🙃c", "🙃", "a🙃b🙃c");
    assertStringTrimLeft("UTF8_BINARY", "a🙃b🙃c", "a", "🙃b🙃c");
    assertStringTrimLeft("UTF8_LCASE", "a🙃b🙃c", "a", "🙃b🙃c");
    assertStringTrimLeft("UNICODE", "a🙃b🙃c", "a", "🙃b🙃c");
    assertStringTrimLeft("UNICODE_CI", "a🙃b🙃c", "a", "🙃b🙃c");
    assertStringTrimLeft("UTF8_BINARY", "a🙃b🙃c", "a🙃", "b🙃c");
    assertStringTrimLeft("UTF8_LCASE", "a🙃b🙃c", "a🙃", "b🙃c");
    assertStringTrimLeft("UNICODE", "a🙃b🙃c", "a🙃", "b🙃c");
    assertStringTrimLeft("UNICODE_CI", "a🙃b🙃c", "a🙃", "b🙃c");
    assertStringTrimLeft("UTF8_BINARY", "a🙃b🙃c", "a🙃b", "c");
    assertStringTrimLeft("UTF8_LCASE", "a🙃b🙃c", "a🙃b", "c");
    assertStringTrimLeft("UNICODE", "a🙃b🙃c", "a🙃b", "c");
    assertStringTrimLeft("UNICODE_CI", "a🙃b🙃c", "a🙃b", "c");
    assertStringTrimLeft("UTF8_BINARY", "a🙃b🙃c", "abc🙃", "");
    assertStringTrimLeft("UTF8_LCASE", "a🙃b🙃c", "abc🙃", "");
    assertStringTrimLeft("UNICODE", "a🙃b🙃c", "abc🙃", "");
    assertStringTrimLeft("UNICODE_CI", "a🙃b🙃c", "abc🙃", "");
    assertStringTrimLeft("UTF8_BINARY", "😀😆😃😄", "😆😃", "😀😆😃😄");
    assertStringTrimLeft("UTF8_LCASE", "😀😆😃😄", "😆😃", "😀😆😃😄");
    assertStringTrimLeft("UNICODE", "😀😆😃😄", "😆😃", "😀😆😃😄");
    assertStringTrimLeft("UNICODE_CI", "😀😆😃😄", "😆😃", "😀😆😃😄");
    assertStringTrimLeft("UTF8_BINARY", "😀😆😃😄", "😀😆", "😃😄");
    assertStringTrimLeft("UTF8_LCASE", "😀😆😃😄", "😀😆", "😃😄");
    assertStringTrimLeft("UNICODE", "😀😆😃😄", "😀😆", "😃😄");
    assertStringTrimLeft("UNICODE_CI", "😀😆😃😄", "😀😆", "😃😄");
    assertStringTrimLeft("UTF8_BINARY", "😀😆😃😄", "😀😆😃😄", "");
    assertStringTrimLeft("UTF8_LCASE", "😀😆😃😄", "😀😆😃😄", "");
    assertStringTrimLeft("UNICODE", "😀😆😃😄", "😀😆😃😄", "");
    assertStringTrimLeft("UNICODE_CI", "😀😆😃😄", "😀😆😃😄", "");
    assertStringTrimLeft("UTF8_BINARY", "𐐅", "𐐅", "");
    assertStringTrimLeft("UTF8_LCASE", "𐐅", "𐐅", "");
    assertStringTrimLeft("UNICODE", "𐐅", "𐐅", "");
    assertStringTrimLeft("UNICODE_CI", "𐐅", "𐐅", "");
    assertStringTrimLeft("UTF8_BINARY", "𐐅", "𐐭", "𐐅");
    assertStringTrimLeft("UTF8_LCASE", "𐐅", "𐐭", "");
    assertStringTrimLeft("UNICODE", "𐐅", "𐐭", "𐐅");
    assertStringTrimLeft("UNICODE_CI", "𐐅", "𐐭", "");
    assertStringTrimLeft("UTF8_BINARY", "𝔸", "𝔸", "");
    assertStringTrimLeft("UTF8_LCASE", "𝔸", "𝔸", "");
    assertStringTrimLeft("UNICODE", "𝔸", "𝔸", "");
    assertStringTrimLeft("UNICODE_CI", "𝔸", "𝔸", "");
    assertStringTrimLeft("UTF8_BINARY", "𝔸", "A", "𝔸");
    assertStringTrimLeft("UTF8_LCASE", "𝔸", "A", "𝔸");
    assertStringTrimLeft("UNICODE", "𝔸", "A", "𝔸");
    assertStringTrimLeft("UNICODE_CI", "𝔸", "A", "");
    assertStringTrimLeft("UTF8_BINARY", "𝔸", "a", "𝔸");
    assertStringTrimLeft("UTF8_LCASE", "𝔸", "a", "𝔸");
    assertStringTrimLeft("UNICODE", "𝔸", "a", "𝔸");
    assertStringTrimLeft("UNICODE_CI", "𝔸", "a", "");
  }

  /**
   * Verify the behaviour of the `StringTrimRight` collation support class.
   */

  private void assertStringTrimRight(String collationName, String sourceString, String trimString,
      String expected) throws SparkException {
    // Prepare the input and expected result.
    int collationId = CollationFactory.collationNameToId(collationName);
    UTF8String src = UTF8String.fromString(sourceString);
    UTF8String trim = UTF8String.fromString(trimString);
    UTF8String result;

    if (trimString == null) {
      // Trim string is ASCII space.
      result = CollationSupport.StringTrimRight.exec(src);
    } else {
      // Trim string is specified.
      result = CollationSupport.StringTrimRight.exec(src, trim, collationId);
    }

    // Test that StringTrimRight result is as expected.
    assertEquals(UTF8String.fromString(expected), result);
  }

  @Test
  public void testStringTrimRight() throws SparkException {
    // Basic tests.
    assertStringTrimRight("UTF8_BINARY", "", "", "");
    assertStringTrimRight("UTF8_BINARY", "", "xyz", "");
    assertStringTrimRight("UTF8_BINARY", "asd", "", "asd");
    assertStringTrimRight("UTF8_BINARY", "asd", null, "asd");
    assertStringTrimRight("UTF8_BINARY", "  asd  ", null, "  asd");
    assertStringTrimRight("UTF8_BINARY", " a世a ", null, " a世a");
    assertStringTrimRight("UTF8_BINARY", "asd", "x", "asd");
    assertStringTrimRight("UTF8_BINARY", "xxasdxx", "x", "xxasd");
    assertStringTrimRight("UTF8_BINARY", "xa世ax", "x", "xa世a");
    assertStringTrimRight("UTF8_LCASE", "", "", "");
    assertStringTrimRight("UTF8_LCASE", "", "xyz", "");
    assertStringTrimRight("UTF8_LCASE", "asd", "", "asd");
    assertStringTrimRight("UTF8_LCASE", "asd", null, "asd");
    assertStringTrimRight("UTF8_LCASE", "  asd  ", null, "  asd");
    assertStringTrimRight("UTF8_LCASE", " a世a ", null, " a世a");
    assertStringTrimRight("UTF8_LCASE", "asd", "x", "asd");
    assertStringTrimRight("UTF8_LCASE", "xxasdxx", "x", "xxasd");
    assertStringTrimRight("UTF8_LCASE", "xa世ax", "x", "xa世a");
    assertStringTrimRight("UNICODE", "", "", "");
    assertStringTrimRight("UNICODE", "", "xyz", "");
    assertStringTrimRight("UNICODE", "asd", "", "asd");
    assertStringTrimRight("UNICODE", "asd", null, "asd");
    assertStringTrimRight("UNICODE", "  asd  ", null, "  asd");
    assertStringTrimRight("UNICODE", " a世a ", null, " a世a");
    assertStringTrimRight("UNICODE", "asd", "x", "asd");
    assertStringTrimRight("UNICODE", "xxasdxx", "x", "xxasd");
    assertStringTrimRight("UNICODE", "xa世ax", "x", "xa世a");
    assertStringTrimRight("UNICODE_CI", "", "", "");
    assertStringTrimRight("UNICODE_CI", "", "xyz", "");
    assertStringTrimRight("UNICODE_CI", "asd", "", "asd");
    assertStringTrimRight("UNICODE_CI", "asd", null, "asd");
    assertStringTrimRight("UNICODE_CI", "  asd  ", null, "  asd");
    assertStringTrimRight("UNICODE_CI", " a世a ", null, " a世a");
    assertStringTrimRight("UNICODE_CI", "asd", "x", "asd");
    assertStringTrimRight("UNICODE_CI", "xxasdxx", "x", "xxasd");
    assertStringTrimRight("UNICODE_CI", "xa世ax", "x", "xa世a");
    // Case variation.
    assertStringTrimRight("UTF8_BINARY", "ddsXXXaa", "asd", "ddsXXX");
    assertStringTrimRight("UTF8_LCASE", "ddsXXXaa", "AsD", "ddsXXX");
    assertStringTrimRight("UNICODE", "ddsXXXaa", "asd", "ddsXXX");
    assertStringTrimRight("UNICODE_CI", "ddsXXXaa", "AsD", "ddsXXX");
    // One-to-many case mapping (e.g. Turkish dotted I)..
    assertStringTrimRight("UTF8_BINARY", "ẞaaaẞ", "ß", "ẞaaaẞ");
    assertStringTrimRight("UTF8_BINARY", "ßaaaß", "ẞ", "ßaaaß");
    assertStringTrimRight("UTF8_BINARY", "Ëaaaẞ", "Ëẞ", "Ëaaa");
    assertStringTrimRight("UTF8_LCASE", "ẞaaaẞ", "ß", "ẞaaa");
    assertStringTrimRight("UTF8_LCASE", "ßaaaß", "ẞ", "ßaaa");
    assertStringTrimRight("UTF8_LCASE", "Ëaaaẞ", "Ëẞ", "Ëaaa");
    assertStringTrimRight("UNICODE", "ẞaaaẞ", "ß", "ẞaaaẞ");
    assertStringTrimRight("UNICODE", "ßaaaß", "ẞ", "ßaaaß");
    assertStringTrimRight("UNICODE", "Ëaaaẞ", "Ëẞ", "Ëaaa");
    assertStringTrimRight("UNICODE_CI", "ẞaaaẞ", "ß", "ẞaaa");
    assertStringTrimRight("UNICODE_CI", "ßaaaß", "ẞ", "ßaaa");
    assertStringTrimRight("UNICODE_CI", "Ëaaaẞ", "Ëẞ", "Ëaaa");
    // One-to-many case mapping (e.g. Turkish dotted I).
    assertStringTrimRight("UTF8_BINARY", "i", "i", "");
    assertStringTrimRight("UTF8_BINARY", "iii", "I", "iii");
    assertStringTrimRight("UTF8_BINARY", "I", "iii", "I");
    assertStringTrimRight("UTF8_BINARY", "ixi", "i", "ix");
    assertStringTrimRight("UTF8_BINARY", "i", "İ", "i");
    assertStringTrimRight("UTF8_BINARY", "i\u0307", "İ", "i\u0307");
    assertStringTrimRight("UTF8_BINARY", "i\u0307", "i", "i\u0307");
    assertStringTrimRight("UTF8_BINARY", "i\u0307", "\u0307", "i");
    assertStringTrimRight("UTF8_BINARY", "i\u0307", "i\u0307", "");
    assertStringTrimRight("UTF8_BINARY", "i\u0307i\u0307", "i\u0307", "");
    assertStringTrimRight("UTF8_BINARY", "i\u0307\u0307", "i\u0307", "");
    assertStringTrimRight("UTF8_BINARY", "i\u0307i", "i\u0307", "");
    assertStringTrimRight("UTF8_BINARY", "i\u0307i", "İ", "i\u0307i");
    assertStringTrimRight("UTF8_BINARY", "i\u0307İ", "i\u0307", "i\u0307İ");
    assertStringTrimRight("UTF8_BINARY", "i\u0307İ", "İ", "i\u0307");
    assertStringTrimRight("UTF8_BINARY", "İ", "İ", "");
    assertStringTrimRight("UTF8_BINARY", "IXi", "İ", "IXi");
    assertStringTrimRight("UTF8_BINARY", "ix\u0307", "Ixİ", "ix\u0307");
    assertStringTrimRight("UTF8_BINARY", "i\u0307x", "IXİ", "i\u0307x");
    assertStringTrimRight("UTF8_BINARY", "i\u0307x", "ix\u0307İ", "");
    assertStringTrimRight("UTF8_BINARY", "İ", "i", "İ");
    assertStringTrimRight("UTF8_BINARY", "İ", "\u0307", "İ");
    assertStringTrimRight("UTF8_BINARY", "Ixİ", "i\u0307", "Ixİ");
    assertStringTrimRight("UTF8_BINARY", "IXİ", "ix\u0307", "IXİ");
    assertStringTrimRight("UTF8_BINARY", "xi\u0307", "\u0307IX", "xi");
    assertStringTrimRight("UTF8_LCASE", "i", "i", "");
    assertStringTrimRight("UTF8_LCASE", "iii", "I", "");
    assertStringTrimRight("UTF8_LCASE", "I", "iii", "");
    assertStringTrimRight("UTF8_LCASE", "ixi", "i", "ix");
    assertStringTrimRight("UTF8_LCASE", "i", "İ", "i");
    assertStringTrimRight("UTF8_LCASE", "i\u0307", "İ", "");
    assertStringTrimRight("UTF8_LCASE", "i\u0307", "i", "i\u0307");
    assertStringTrimRight("UTF8_LCASE", "i\u0307", "\u0307", "i");
    assertStringTrimRight("UTF8_LCASE", "i\u0307", "i\u0307", "");
    assertStringTrimRight("UTF8_LCASE", "i\u0307i\u0307", "i\u0307", "");
    assertStringTrimRight("UTF8_LCASE", "i\u0307\u0307", "i\u0307", "");
    assertStringTrimRight("UTF8_LCASE", "i\u0307i", "i\u0307", "");
    assertStringTrimRight("UTF8_LCASE", "i\u0307i", "İ", "i\u0307i");
    assertStringTrimRight("UTF8_LCASE", "i\u0307İ", "i\u0307", "i\u0307İ");
    assertStringTrimRight("UTF8_LCASE", "i\u0307İ", "İ", "");
    assertStringTrimRight("UTF8_LCASE", "İ", "İ", "");
    assertStringTrimRight("UTF8_LCASE", "IXi", "İ", "IXi");
    assertStringTrimRight("UTF8_LCASE", "ix\u0307", "Ixİ", "ix\u0307");
    assertStringTrimRight("UTF8_LCASE", "i\u0307x", "IXİ", "");
    assertStringTrimRight("UTF8_LCASE", "i\u0307x", "I\u0307xİ", "");
    assertStringTrimRight("UTF8_LCASE", "İ", "i", "İ");
    assertStringTrimRight("UTF8_LCASE", "İ", "\u0307", "İ");
    assertStringTrimRight("UTF8_LCASE", "Ixİ", "i\u0307", "Ixİ");
    assertStringTrimRight("UTF8_LCASE", "IXİ", "ix\u0307", "IXİ");
    assertStringTrimRight("UTF8_LCASE", "xi\u0307", "\u0307IX", "");
    assertStringTrimRight("UNICODE", "i", "i", "");
    assertStringTrimRight("UNICODE", "iii", "I", "iii");
    assertStringTrimRight("UNICODE", "I", "iii", "I");
    assertStringTrimRight("UNICODE", "ixi", "i", "ix");
    assertStringTrimRight("UNICODE", "i", "İ", "i");
    assertStringTrimRight("UNICODE", "i\u0307", "İ", "i\u0307");
    assertStringTrimRight("UNICODE", "i\u0307", "i", "i\u0307");
    assertStringTrimRight("UNICODE", "i\u0307", "\u0307", "i\u0307");
    assertStringTrimRight("UNICODE", "i\u0307", "i\u0307", "i\u0307");
    assertStringTrimRight("UNICODE", "i\u0307i\u0307", "i\u0307", "i\u0307i\u0307");
    assertStringTrimRight("UNICODE", "i\u0307\u0307", "i\u0307", "i\u0307\u0307");
    assertStringTrimRight("UNICODE", "i\u0307i", "i\u0307", "i\u0307");
    assertStringTrimRight("UNICODE", "i\u0307i", "İ", "i\u0307i");
    assertStringTrimRight("UNICODE", "i\u0307İ", "i\u0307", "i\u0307İ");
    assertStringTrimRight("UNICODE", "i\u0307İ", "İ", "i\u0307");
    assertStringTrimRight("UNICODE", "İ", "İ", "");
    assertStringTrimRight("UNICODE", "IXi", "İ", "IXi");
    assertStringTrimRight("UNICODE", "ix\u0307", "Ixİ", "ix\u0307");
    assertStringTrimRight("UNICODE", "i\u0307x", "IXİ", "i\u0307x");
    assertStringTrimRight("UNICODE", "i\u0307x", "ix\u0307İ", "i\u0307");
    assertStringTrimRight("UNICODE", "İ", "i", "İ");
    assertStringTrimRight("UNICODE", "İ", "\u0307", "İ");
    assertStringTrimRight("UNICODE", "i\u0307", "i\u0307", "i\u0307");
    assertStringTrimRight("UNICODE", "Ixİ", "i\u0307", "Ixİ");
    assertStringTrimRight("UNICODE", "IXİ", "ix\u0307", "IXİ");
    assertStringTrimRight("UNICODE", "xi\u0307", "\u0307IX", "xi\u0307");
    assertStringTrimRight("UNICODE_CI", "i", "i", "");
    assertStringTrimRight("UNICODE_CI", "iii", "I", "");
    assertStringTrimRight("UNICODE_CI", "I", "iii", "");
    assertStringTrimRight("UNICODE_CI", "ixi", "i", "ix");
    assertStringTrimRight("UNICODE_CI", "i", "İ", "i");
    assertStringTrimRight("UNICODE_CI", "i\u0307", "İ", "");
    assertStringTrimRight("UNICODE_CI", "i\u0307", "i", "i\u0307");
    assertStringTrimRight("UNICODE_CI", "i\u0307", "\u0307", "i\u0307");
    assertStringTrimRight("UNICODE_CI", "i\u0307", "i\u0307", "i\u0307");
    assertStringTrimRight("UNICODE_CI", "i\u0307i\u0307", "i\u0307", "i\u0307i\u0307");
    assertStringTrimRight("UNICODE_CI", "i\u0307\u0307", "i\u0307", "i\u0307\u0307");
    assertStringTrimRight("UNICODE_CI", "i\u0307i", "i\u0307", "i\u0307");
    assertStringTrimRight("UNICODE_CI", "i\u0307i", "İ", "i\u0307i");
    assertStringTrimRight("UNICODE_CI", "i\u0307İ", "i\u0307", "i\u0307İ");
    assertStringTrimRight("UNICODE_CI", "i\u0307İ", "İ", "");
    assertStringTrimRight("UNICODE_CI", "İ", "İ", "");
    assertStringTrimRight("UNICODE_CI", "IXi", "İ", "IXi");
    assertStringTrimRight("UNICODE_CI", "ix\u0307", "Ixİ", "ix\u0307");
    assertStringTrimRight("UNICODE_CI", "i\u0307x", "IXİ", "");
    assertStringTrimRight("UNICODE_CI", "i\u0307x", "I\u0307xİ", "");
    assertStringTrimRight("UNICODE_CI", "İ", "i", "İ");
    assertStringTrimRight("UNICODE_CI", "İ", "\u0307", "İ");
    assertStringTrimRight("UNICODE_CI", "i\u0307", "i\u0307", "i\u0307");
    assertStringTrimRight("UNICODE_CI", "Ixİ", "i\u0307", "Ixİ");
    assertStringTrimRight("UNICODE_CI", "IXİ", "ix\u0307", "IXİ");
    assertStringTrimRight("UNICODE_CI", "xi\u0307", "\u0307IX", "xi\u0307");
    // Conditional case mapping (e.g. Greek sigmas).
    assertStringTrimRight("UTF8_BINARY", "ςxς", "σ", "ςxς");
    assertStringTrimRight("UTF8_BINARY", "ςxς", "ς", "ςx");
    assertStringTrimRight("UTF8_BINARY", "ςxς", "Σ", "ςxς");
    assertStringTrimRight("UTF8_BINARY", "σxσ", "σ", "σx");
    assertStringTrimRight("UTF8_BINARY", "σxσ", "ς", "σxσ");
    assertStringTrimRight("UTF8_BINARY", "σxσ", "Σ", "σxσ");
    assertStringTrimRight("UTF8_BINARY", "ΣxΣ", "σ", "ΣxΣ");
    assertStringTrimRight("UTF8_BINARY", "ΣxΣ", "ς", "ΣxΣ");
    assertStringTrimRight("UTF8_BINARY", "ΣxΣ", "Σ", "Σx");
    assertStringTrimRight("UTF8_LCASE", "ςxς", "σ", "ςx");
    assertStringTrimRight("UTF8_LCASE", "ςxς", "ς", "ςx");
    assertStringTrimRight("UTF8_LCASE", "ςxς", "Σ", "ςx");
    assertStringTrimRight("UTF8_LCASE", "σxσ", "σ", "σx");
    assertStringTrimRight("UTF8_LCASE", "σxσ", "ς", "σx");
    assertStringTrimRight("UTF8_LCASE", "σxσ", "Σ", "σx");
    assertStringTrimRight("UTF8_LCASE", "ΣxΣ", "σ", "Σx");
    assertStringTrimRight("UTF8_LCASE", "ΣxΣ", "ς", "Σx");
    assertStringTrimRight("UTF8_LCASE", "ΣxΣ", "Σ", "Σx");
    assertStringTrimRight("UNICODE", "ςxς", "σ", "ςxς");
    assertStringTrimRight("UNICODE", "ςxς", "ς", "ςx");
    assertStringTrimRight("UNICODE", "ςxς", "Σ", "ςxς");
    assertStringTrimRight("UNICODE", "σxσ", "σ", "σx");
    assertStringTrimRight("UNICODE", "σxσ", "ς", "σxσ");
    assertStringTrimRight("UNICODE", "σxσ", "Σ", "σxσ");
    assertStringTrimRight("UNICODE", "ΣxΣ", "σ", "ΣxΣ");
    assertStringTrimRight("UNICODE", "ΣxΣ", "ς", "ΣxΣ");
    assertStringTrimRight("UNICODE", "ΣxΣ", "Σ", "Σx");
    assertStringTrimRight("UNICODE_CI", "ςxς", "σ", "ςx");
    assertStringTrimRight("UNICODE_CI", "ςxς", "ς", "ςx");
    assertStringTrimRight("UNICODE_CI", "ςxς", "Σ", "ςx");
    assertStringTrimRight("UNICODE_CI", "σxσ", "σ", "σx");
    assertStringTrimRight("UNICODE_CI", "σxσ", "ς", "σx");
    assertStringTrimRight("UNICODE_CI", "σxσ", "Σ", "σx");
    assertStringTrimRight("UNICODE_CI", "ΣxΣ", "σ", "Σx");
    assertStringTrimRight("UNICODE_CI", "ΣxΣ", "ς", "Σx");
    assertStringTrimRight("UNICODE_CI", "ΣxΣ", "Σ", "Σx");
    // Unicode normalization.
    assertStringTrimRight("UTF8_BINARY", "åβγδa\u030A", "å", "åβγδa\u030A");
    assertStringTrimRight("UTF8_LCASE", "åβγδa\u030A", "Å", "åβγδa\u030A");
    assertStringTrimRight("UNICODE", "åβγδa\u030A", "å", "åβγδ");
    assertStringTrimRight("UNICODE_CI", "åβγδa\u030A", "Å", "åβγδ");
    // Surrogate pairs.
    assertStringTrimRight("UTF8_BINARY", "a🙃b🙃c", "🙃", "a🙃b🙃c");
    assertStringTrimRight("UTF8_LCASE", "a🙃b🙃c", "🙃", "a🙃b🙃c");
    assertStringTrimRight("UNICODE", "a🙃b🙃c", "🙃", "a🙃b🙃c");
    assertStringTrimRight("UNICODE_CI", "a🙃b🙃c", "🙃", "a🙃b🙃c");
    assertStringTrimRight("UTF8_BINARY", "a🙃b🙃c", "c", "a🙃b🙃");
    assertStringTrimRight("UTF8_LCASE", "a🙃b🙃c", "c", "a🙃b🙃");
    assertStringTrimRight("UNICODE", "a🙃b🙃c", "c", "a🙃b🙃");
    assertStringTrimRight("UNICODE_CI", "a🙃b🙃c", "c", "a🙃b🙃");
    assertStringTrimRight("UTF8_BINARY", "a🙃b🙃c", "c🙃", "a🙃b");
    assertStringTrimRight("UTF8_LCASE", "a🙃b🙃c", "c🙃", "a🙃b");
    assertStringTrimRight("UNICODE", "a🙃b🙃c", "c🙃", "a🙃b");
    assertStringTrimRight("UNICODE_CI", "a🙃b🙃c", "c🙃", "a🙃b");
    assertStringTrimRight("UTF8_BINARY", "a🙃b🙃c", "c🙃b", "a");
    assertStringTrimRight("UTF8_LCASE", "a🙃b🙃c", "c🙃b", "a");
    assertStringTrimRight("UNICODE", "a🙃b🙃c", "c🙃b", "a");
    assertStringTrimRight("UNICODE_CI", "a🙃b🙃c", "c🙃b", "a");
    assertStringTrimRight("UTF8_BINARY", "a🙃b🙃c", "abc🙃", "");
    assertStringTrimRight("UTF8_LCASE", "a🙃b🙃c", "abc🙃", "");
    assertStringTrimRight("UNICODE", "a🙃b🙃c", "abc🙃", "");
    assertStringTrimRight("UNICODE_CI", "a🙃b🙃c", "abc🙃", "");
    assertStringTrimRight("UTF8_BINARY", "😀😆😃😄", "😆😃", "😀😆😃😄");
    assertStringTrimRight("UTF8_LCASE", "😀😆😃😄", "😆😃", "😀😆😃😄");
    assertStringTrimRight("UNICODE", "😀😆😃😄", "😆😃", "😀😆😃😄");
    assertStringTrimRight("UNICODE_CI", "😀😆😃😄", "😆😃", "😀😆😃😄");
    assertStringTrimRight("UTF8_BINARY", "😀😆😃😄", "😃😄", "😀😆");
    assertStringTrimRight("UTF8_LCASE", "😀😆😃😄", "😃😄", "😀😆");
    assertStringTrimRight("UNICODE", "😀😆😃😄", "😃😄", "😀😆");
    assertStringTrimRight("UNICODE_CI", "😀😆😃😄", "😃😄", "😀😆");
    assertStringTrimRight("UTF8_BINARY", "😀😆😃😄", "😀😆😃😄", "");
    assertStringTrimRight("UTF8_LCASE", "😀😆😃😄", "😀😆😃😄", "");
    assertStringTrimRight("UNICODE", "😀😆😃😄", "😀😆😃😄", "");
    assertStringTrimRight("UNICODE_CI", "😀😆😃😄", "😀😆😃😄", "");
    assertStringTrimRight("UTF8_BINARY", "𐐅", "𐐅", "");
    assertStringTrimRight("UTF8_LCASE", "𐐅", "𐐅", "");
    assertStringTrimRight("UNICODE", "𐐅", "𐐅", "");
    assertStringTrimRight("UNICODE_CI", "𐐅", "𐐅", "");
    assertStringTrimRight("UTF8_BINARY", "𐐅", "𐐭", "𐐅");
    assertStringTrimRight("UTF8_LCASE", "𐐅", "𐐭", "");
    assertStringTrimRight("UNICODE", "𐐅", "𐐭", "𐐅");
    assertStringTrimRight("UNICODE_CI", "𐐅", "𐐭", "");
    assertStringTrimRight("UTF8_BINARY", "𝔸", "𝔸", "");
    assertStringTrimRight("UTF8_LCASE", "𝔸", "𝔸", "");
    assertStringTrimRight("UNICODE", "𝔸", "𝔸", "");
    assertStringTrimRight("UNICODE_CI", "𝔸", "𝔸", "");
    assertStringTrimRight("UTF8_BINARY", "𝔸", "A", "𝔸");
    assertStringTrimRight("UTF8_LCASE", "𝔸", "A", "𝔸");
    assertStringTrimRight("UNICODE", "𝔸", "A", "𝔸");
    assertStringTrimRight("UNICODE_CI", "𝔸", "A", "");
    assertStringTrimRight("UTF8_BINARY", "𝔸", "a", "𝔸");
    assertStringTrimRight("UTF8_LCASE", "𝔸", "a", "𝔸");
    assertStringTrimRight("UNICODE", "𝔸", "a", "𝔸");
    assertStringTrimRight("UNICODE_CI", "𝔸", "a", "");
  }

  private void assertStringTranslate(
      String inputString,
      String matchingString,
      String replaceString,
      String collationName,
      String expectedResultString) throws SparkException {
    int collationId = CollationFactory.collationNameToId(collationName);
    Map<String, String> dict = buildDict(matchingString, replaceString);
    UTF8String source = UTF8String.fromString(inputString);
    UTF8String result = CollationSupport.StringTranslate.exec(source, dict, collationId);
    assertEquals(expectedResultString, result.toString());
  }

  @Test
  public void testStringTranslate() throws SparkException {
    // Basic tests - UTF8_BINARY.
    assertStringTranslate("Translate", "Rnlt", "12", "UTF8_BINARY", "Tra2sae");
    assertStringTranslate("Translate", "Rn", "1234", "UTF8_BINARY", "Tra2slate");
    assertStringTranslate("Translate", "Rnlt", "1234", "UTF8_BINARY", "Tra2s3a4e");
    assertStringTranslate("TRanslate", "rnlt", "XxXx", "UTF8_BINARY", "TRaxsXaxe");
    assertStringTranslate("TRanslater", "Rrnlt", "xXxXx", "UTF8_BINARY", "TxaxsXaxeX");
    assertStringTranslate("TRanslater", "Rrnlt", "XxxXx", "UTF8_BINARY", "TXaxsXaxex");
    assertStringTranslate("test大千世界X大千世界", "界x", "AB", "UTF8_BINARY", "test大千世AX大千世A");
    assertStringTranslate("大千世界test大千世界", "TEST", "abcd", "UTF8_BINARY", "大千世界test大千世界");
    assertStringTranslate("Test大千世界大千世界", "tT", "oO", "UTF8_BINARY", "Oeso大千世界大千世界");
    assertStringTranslate("大千世界大千世界tesT", "Tt", "Oo", "UTF8_BINARY", "大千世界大千世界oesO");
    assertStringTranslate("大千世界大千世界tesT", "大千", "世世", "UTF8_BINARY", "世世世界世世世界tesT");
    assertStringTranslate("Translate", "Rnlasdfjhgadt", "1234", "UTF8_BINARY", "Tr4234e");
    assertStringTranslate("Translate", "Rnlt", "123495834634", "UTF8_BINARY", "Tra2s3a4e");
    assertStringTranslate("abcdef", "abcde", "123", "UTF8_BINARY", "123f");
    // Basic tests - UTF8_LCASE.
    assertStringTranslate("Translate", "Rnlt", "12", "UTF8_LCASE", "1a2sae");
    assertStringTranslate("Translate", "Rn", "1234", "UTF8_LCASE", "T1a2slate");
    assertStringTranslate("Translate", "Rnlt", "1234", "UTF8_LCASE", "41a2s3a4e");
    assertStringTranslate("TRanslate", "rnlt", "XxXx", "UTF8_LCASE", "xXaxsXaxe");
    assertStringTranslate("TRanslater", "Rrnlt", "xXxXx", "UTF8_LCASE", "xxaxsXaxex");
    assertStringTranslate("TRanslater", "Rrnlt", "XxxXx", "UTF8_LCASE", "xXaxsXaxeX");
    assertStringTranslate("test大千世界X大千世界", "界x", "AB", "UTF8_LCASE", "test大千世AB大千世A");
    assertStringTranslate("大千世界test大千世界", "TEST", "abcd", "UTF8_LCASE", "大千世界abca大千世界");
    assertStringTranslate("Test大千世界大千世界", "tT", "oO", "UTF8_LCASE", "oeso大千世界大千世界");
    assertStringTranslate("大千世界大千世界tesT", "Tt", "Oo", "UTF8_LCASE", "大千世界大千世界OesO");
    assertStringTranslate("大千世界大千世界tesT", "大千", "世世", "UTF8_LCASE", "世世世界世世世界tesT");
    assertStringTranslate("Translate", "Rnlasdfjhgadt", "1234", "UTF8_LCASE", "14234e");
    assertStringTranslate("Translate", "Rnlt", "123495834634", "UTF8_LCASE", "41a2s3a4e");
    assertStringTranslate("abcdef", "abcde", "123", "UTF8_LCASE", "123f");
    // Basic tests - UNICODE.
    assertStringTranslate("Translate", "Rnlt", "12", "UNICODE", "Tra2sae");
    assertStringTranslate("Translate", "Rn", "1234", "UNICODE", "Tra2slate");
    assertStringTranslate("Translate", "Rnlt", "1234", "UNICODE", "Tra2s3a4e");
    assertStringTranslate("TRanslate", "rnlt", "XxXx", "UNICODE", "TRaxsXaxe");
    assertStringTranslate("TRanslater", "Rrnlt", "xXxXx", "UNICODE", "TxaxsXaxeX");
    assertStringTranslate("TRanslater", "Rrnlt", "XxxXx", "UNICODE", "TXaxsXaxex");
    assertStringTranslate("test大千世界X大千世界", "界x", "AB", "UNICODE", "test大千世AX大千世A");
    assertStringTranslate("大千世界test大千世界", "TEST", "abcd", "UNICODE", "大千世界test大千世界");
    assertStringTranslate("Test大千世界大千世界", "tT", "oO", "UNICODE", "Oeso大千世界大千世界");
    assertStringTranslate("大千世界大千世界tesT", "Tt", "Oo", "UNICODE", "大千世界大千世界oesO");
    assertStringTranslate("大千世界大千世界tesT", "大千", "世世", "UNICODE", "世世世界世世世界tesT");
    assertStringTranslate("Translate", "Rnlasdfjhgadt", "1234", "UNICODE", "Tr4234e");
    assertStringTranslate("Translate", "Rnlt", "123495834634", "UNICODE", "Tra2s3a4e");
    assertStringTranslate("abcdef", "abcde", "123", "UNICODE", "123f");
    // Basic tests - UNICODE_CI.
    assertStringTranslate("Translate", "Rnlt", "12", "UNICODE_CI", "1a2sae");
    assertStringTranslate("Translate", "Rn", "1234", "UNICODE_CI", "T1a2slate");
    assertStringTranslate("Translate", "Rnlt", "1234", "UNICODE_CI", "41a2s3a4e");
    assertStringTranslate("TRanslate", "rnlt", "XxXx", "UNICODE_CI", "xXaxsXaxe");
    assertStringTranslate("TRanslater", "Rrnlt", "xXxXx", "UNICODE_CI", "xxaxsXaxex");
    assertStringTranslate("TRanslater", "Rrnlt", "XxxXx", "UNICODE_CI", "xXaxsXaxeX");
    assertStringTranslate("test大千世界X大千世界", "界x", "AB", "UNICODE_CI", "test大千世AB大千世A");
    assertStringTranslate("大千世界test大千世界", "TEST", "abcd", "UNICODE_CI", "大千世界abca大千世界");
    assertStringTranslate("Test大千世界大千世界", "tT", "oO", "UNICODE_CI", "oeso大千世界大千世界");
    assertStringTranslate("大千世界大千世界tesT", "Tt", "Oo", "UNICODE_CI", "大千世界大千世界OesO");
    assertStringTranslate("大千世界大千世界tesT", "大千", "世世", "UNICODE_CI", "世世世界世世世界tesT");
    assertStringTranslate("Translate", "Rnlasdfjhgadt", "1234", "UNICODE_CI", "14234e");
    assertStringTranslate("Translate", "Rnlt", "123495834634", "UNICODE_CI", "41a2s3a4e");
    assertStringTranslate("abcdef", "abcde", "123", "UNICODE_CI", "123f");

    // One-to-many case mapping - UTF8_BINARY.
    assertStringTranslate("İ", "i\u0307", "xy", "UTF8_BINARY", "İ");
    assertStringTranslate("i\u0307", "İ", "xy", "UTF8_BINARY", "i\u0307");
    assertStringTranslate("i\u030A", "İ", "x", "UTF8_BINARY", "i\u030A");
    assertStringTranslate("i\u030A", "İi", "xy", "UTF8_BINARY", "y\u030A");
    assertStringTranslate("İi\u0307", "İi\u0307", "123", "UTF8_BINARY", "123");
    assertStringTranslate("İi\u0307", "İyz", "123", "UTF8_BINARY", "1i\u0307");
    assertStringTranslate("İi\u0307", "xi\u0307", "123", "UTF8_BINARY", "İ23");
    assertStringTranslate("a\u030Abcå", "a\u030Aå", "123", "UTF8_BINARY", "12bc3");
    assertStringTranslate("a\u030Abcå", "A\u030AÅ", "123", "UTF8_BINARY", "a2bcå");
    assertStringTranslate("a\u030AβφδI\u0307", "Iİaå", "1234", "UTF8_BINARY", "3\u030Aβφδ1\u0307");
    // One-to-many case mapping - UTF8_LCASE.
    assertStringTranslate("İ", "i\u0307", "xy", "UTF8_LCASE", "İ");
    assertStringTranslate("i\u0307", "İ", "xy", "UTF8_LCASE", "x");
    assertStringTranslate("i\u030A", "İ", "x", "UTF8_LCASE", "i\u030A");
    assertStringTranslate("i\u030A", "İi", "xy", "UTF8_LCASE", "y\u030A");
    assertStringTranslate("İi\u0307", "İi\u0307", "123", "UTF8_LCASE", "11");
    assertStringTranslate("İi\u0307", "İyz", "123", "UTF8_LCASE", "11");
    assertStringTranslate("İi\u0307", "xi\u0307", "123", "UTF8_LCASE", "İ23");
    assertStringTranslate("a\u030Abcå", "a\u030Aå", "123", "UTF8_LCASE", "12bc3");
    assertStringTranslate("a\u030Abcå", "A\u030AÅ", "123", "UTF8_LCASE", "12bc3");
    assertStringTranslate("A\u030Aβφδi\u0307", "Iİaå", "1234", "UTF8_LCASE", "3\u030Aβφδ2");
    // One-to-many case mapping - UNICODE.
    assertStringTranslate("İ", "i\u0307", "xy", "UNICODE", "İ");
    assertStringTranslate("i\u0307", "İ", "xy", "UNICODE", "i\u0307");
    assertStringTranslate("i\u030A", "İ", "x", "UNICODE", "i\u030A");
    assertStringTranslate("i\u030A", "İi", "xy", "UNICODE", "i\u030A");
    assertStringTranslate("İi\u0307", "İi\u0307", "123", "UNICODE", "1i\u0307");
    assertStringTranslate("İi\u0307", "İyz", "123", "UNICODE", "1i\u0307");
    assertStringTranslate("İi\u0307", "xi\u0307", "123", "UNICODE", "İi\u0307");
    assertStringTranslate("a\u030Abcå", "a\u030Aå", "123", "UNICODE", "3bc3");
    assertStringTranslate("a\u030Abcå", "A\u030AÅ", "123", "UNICODE", "a\u030Abcå");
    assertStringTranslate("a\u030AβφδI\u0307", "Iİaå", "1234", "UNICODE", "4βφδ2");
    // One-to-many case mapping - UNICODE_CI.
    assertStringTranslate("İ", "i\u0307", "xy", "UNICODE_CI", "İ");
    assertStringTranslate("i\u0307", "İ", "xy", "UNICODE_CI", "x");
    assertStringTranslate("i\u030A", "İ", "x", "UNICODE_CI", "i\u030A");
    assertStringTranslate("i\u030A", "İi", "xy", "UNICODE_CI", "i\u030A");
    assertStringTranslate("İi\u0307", "İi\u0307", "123", "UNICODE_CI", "11");
    assertStringTranslate("İi\u0307", "İyz", "123", "UNICODE_CI", "11");
    assertStringTranslate("İi\u0307", "xi\u0307", "123", "UNICODE_CI", "İi\u0307");
    assertStringTranslate("a\u030Abcå", "a\u030Aå", "123", "UNICODE_CI", "3bc3");
    assertStringTranslate("a\u030Abcå", "A\u030AÅ", "123", "UNICODE_CI", "3bc3");
    assertStringTranslate("A\u030Aβφδi\u0307", "Iİaå", "1234", "UNICODE_CI", "4βφδ2");

    // Greek sigmas - UTF8_BINARY.
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "Συη", "σιι", "UTF8_BINARY", "σΥσΤΗΜΑΤΙΚΟσ");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "συη", "σιι", "UTF8_BINARY", "ΣΥΣΤΗΜΑΤΙΚΟΣ");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "ςυη", "σιι", "UTF8_BINARY", "ΣΥΣΤΗΜΑΤΙΚΟΣ");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "συη", "ςιι", "UTF8_BINARY", "ΣΥΣΤΗΜΑΤΙΚΟΣ");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "Συη", "ςιι", "UTF8_BINARY", "ςΥςΤΗΜΑΤΙΚΟς");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "ςυη", "ςιι", "UTF8_BINARY", "ΣΥΣΤΗΜΑΤΙΚΟΣ");
    assertStringTranslate("συστηματικος", "Συη", "σιι", "UTF8_BINARY", "σιστιματικος");
    assertStringTranslate("συστηματικος", "συη", "σιι", "UTF8_BINARY", "σιστιματικος");
    assertStringTranslate("συστηματικος", "ςυη", "σιι", "UTF8_BINARY", "σιστιματικοσ");
    // Greek sigmas - UTF8_LCASE.
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "Συη", "σιι", "UTF8_LCASE", "σισΤιΜΑΤΙΚΟσ");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "συη", "σιι", "UTF8_LCASE", "σισΤιΜΑΤΙΚΟσ");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "ςυη", "σιι", "UTF8_LCASE", "σισΤιΜΑΤΙΚΟσ");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "συη", "ςιι", "UTF8_LCASE", "ςιςΤιΜΑΤΙΚΟς");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "Συη", "ςιι", "UTF8_LCASE", "ςιςΤιΜΑΤΙΚΟς");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "ςυη", "ςιι", "UTF8_LCASE", "ςιςΤιΜΑΤΙΚΟς");
    assertStringTranslate("συστηματικος", "Συη", "σιι", "UTF8_LCASE", "σιστιματικοσ");
    assertStringTranslate("συστηματικος", "συη", "σιι", "UTF8_LCASE", "σιστιματικοσ");
    assertStringTranslate("συστηματικος", "ςυη", "σιι", "UTF8_LCASE", "σιστιματικοσ");
    // Greek sigmas - UNICODE.
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "Συη", "σιι", "UNICODE", "σΥσΤΗΜΑΤΙΚΟσ");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "συη", "σιι", "UNICODE", "ΣΥΣΤΗΜΑΤΙΚΟΣ");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "ςυη", "σιι", "UNICODE", "ΣΥΣΤΗΜΑΤΙΚΟΣ");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "συη", "ςιι", "UNICODE", "ΣΥΣΤΗΜΑΤΙΚΟΣ");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "Συη", "ςιι", "UNICODE", "ςΥςΤΗΜΑΤΙΚΟς");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "ςυη", "ςιι", "UNICODE", "ΣΥΣΤΗΜΑΤΙΚΟΣ");
    assertStringTranslate("συστηματικος", "Συη", "σιι", "UNICODE", "σιστιματικος");
    assertStringTranslate("συστηματικος", "συη", "σιι", "UNICODE", "σιστιματικος");
    assertStringTranslate("συστηματικος", "ςυη", "σιι", "UNICODE", "σιστιματικοσ");
    // Greek sigmas - UNICODE_CI.
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "Συη", "σιι", "UNICODE_CI", "σισΤιΜΑΤΙΚΟσ");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "συη", "σιι", "UNICODE_CI", "σισΤιΜΑΤΙΚΟσ");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "ςυη", "σιι", "UNICODE_CI", "σισΤιΜΑΤΙΚΟσ");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "συη", "ςιι", "UNICODE_CI", "ςιςΤιΜΑΤΙΚΟς");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "Συη", "ςιι", "UNICODE_CI", "ςιςΤιΜΑΤΙΚΟς");
    assertStringTranslate("ΣΥΣΤΗΜΑΤΙΚΟΣ", "ςυη", "ςιι", "UNICODE_CI", "ςιςΤιΜΑΤΙΚΟς");
    assertStringTranslate("συστηματικος", "Συη", "σιι", "UNICODE_CI", "σιστιματικοσ");
    assertStringTranslate("συστηματικος", "συη", "σιι", "UNICODE_CI", "σιστιματικοσ");
    assertStringTranslate("συστηματικος", "ςυη", "σιι", "UNICODE_CI", "σιστιματικοσ");
  }

  private Map<String, String> buildDict(String matching, String replace) {
    Map<String, String> dict = new HashMap<>();
    int i = 0, j = 0;
    while (i < matching.length()) {
      String rep = "\u0000";
      if (j < replace.length()) {
        int repCharCount = Character.charCount(replace.codePointAt(j));
        rep = replace.substring(j, j + repCharCount);
        j += repCharCount;
      }
      int matchCharCount = Character.charCount(matching.codePointAt(i));
      String matchStr = matching.substring(i, i + matchCharCount);
      dict.putIfAbsent(matchStr, rep);
      i += matchCharCount;
    }
    return dict;
  }

}
// checkstyle.on: AvoidEscapedUnicodeCharacters
