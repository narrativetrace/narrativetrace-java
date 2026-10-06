/*
 * Copyright (c) 2026 Empower Agile
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
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.tooling.feedback;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The names and the credential prefixes the value-free gate refuses — the DATA half of {@link
 * ValueFreeRule}'s name and shape rules.
 *
 * <p>INTENT: the runtime's own deny-list ({@code SensitiveVocabulary}, five languages, behind
 * {@code RedactionPolicy}) is the authority on what a sensitive field is called. This library
 * declares zero dependencies and may never link against that runtime, so the vocabulary is restated
 * here as data and the cross-module security suite asserts the only implication that matters: every
 * name the runtime redacts is also refused here. Drift is a build failure, not a leak discovered in
 * a public issue.
 *
 * <p><b>@llmNote</b> Matching here is COARSER than the runtime's on purpose. The runtime
 * distinguishes substring terms from identifier-token terms because blanking {@code circuitBreaker}
 * is how a team switches redaction off entirely; this gate blanks nothing — it refuses to file a
 * report and names the rule — so a near miss costs a sentence, and the only expensive mistake is
 * the one that files a credential.
 */
final class SecretVocabulary {

  /**
   * Every deny-listed term, already canonical (lower case, no diacritics). Matched as a substring
   * of the canonical form of whatever key is being assigned a value.
   */
  private static final List<String> TERMS =
      List.of(
          // credentials
          "password",
          "passwd",
          "passphrase",
          "contrasena",
          "claveacceso",
          "clave_acceso",
          "clavesecreta",
          "clave_secreta",
          "senha",
          "motdepasse",
          "mot_de_passe",
          "passwort",
          "kennwort",
          "密码",
          "mima",
          "token",
          "apikey",
          "api_key",
          "accesskey",
          "access_key",
          "bearer",
          "secret",
          "credential",
          "privatekey",
          "private_key",
          "authorization",
          "otp",
          "mfacode",
          "mfa_code",
          "totp",
          // session
          "sessionid",
          "session_id",
          "cookie",
          "setcookie",
          "set_cookie",
          "jwt",
          // payment
          "cardnumber",
          "card_number",
          "pan",
          "tarjeta",
          "cartao",
          "cartebancaire",
          "carte_bancaire",
          "numerocarte",
          "numero_carte",
          "cvv",
          "accountnumber",
          "account_number",
          "routingnumber",
          "routing_number",
          "iban",
          // national ids
          "ssn",
          "socialsecurity",
          "social_security",
          "socialsecuritynumber",
          "taxid",
          "tax_id",
          "rut",
          "cuit",
          "dni",
          "cedula",
          "cpf",
          "cnpj",
          "nir",
          "身份证",
          "shenfenzheng");

  /**
   * The shapes a value gives itself away by, STRUCTURALLY — no checksum is verified here.
   *
   * <p><b>@llmNote</b> Deliberately not a copy of the runtime's checksum-exact matchers. Copying
   * nine national-id check-digit algorithms into a library that may not link the one that already
   * has them is how two implementations start disagreeing; a shape that is a SUPERSET of theirs
   * cannot. The cross-module security suite proves the superset over the shared corpus, and the
   * price is paid in the right currency: a checksum-failing lookalike is refused with a named rule
   * instead of being filed.
   */
  private static final List<Pattern> SHAPES =
      List.of(
          // A JWT, anchored on the base64url encoding of a JSON object's opening — `{"`.
          Pattern.compile("\\beyJ[A-Za-z0-9_-]{6,}\\.[A-Za-z0-9_-]{6,}"),
          // A PEM private key block header.
          Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
          // Named credential prefixes: the four whose shape is the credential.
          Pattern.compile("\\bghp_[A-Za-z0-9]{8,}"),
          Pattern.compile("\\bgithub_pat_[A-Za-z0-9_]{8,}"),
          Pattern.compile("\\bsk-[A-Za-z0-9_-]{8,}"),
          Pattern.compile("\\bAKIA[0-9A-Z]{12,}"),
          Pattern.compile("\\bxox[abprs]-[A-Za-z0-9-]{8,}"),
          // A Chilean RUT, printed with or without its thousands dots.
          Pattern.compile("\\b\\d{1,2}\\.?\\d{3}\\.?\\d{3}-[\\dkK]\\b"),
          // A Brazilian CPF.
          Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b"),
          // A Brazilian CNPJ.
          Pattern.compile("\\b\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}\\b"),
          // A Spanish DNI or NIE.
          Pattern.compile("\\b[XYZxyz]?\\d{7,8}-?[A-Za-z]\\b"),
          // A French NIR, bare or in the spaced form a card is printed in, Corsica included.
          Pattern.compile(
              "\\b[12]\\s?\\d{2}\\s?\\d{2}\\s?(\\d{2}|\\d?[AB])\\s?\\d{3}\\s?\\d{3}\\s?\\d{2}\\b"),
          // A Chinese resident identity card.
          Pattern.compile("\\b\\d{17}[\\dXx]\\b"),
          // A payment card number's digit length.
          Pattern.compile("\\b\\d{13,19}\\b"));

  private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

  private SecretVocabulary() {}

  /** Whether any secret-shaped value appears anywhere in this text. */
  static boolean containsASecretShape(String text) {
    return SHAPES.stream().anyMatch(shape -> shape.matcher(text).find());
  }

  /** Whether the canonical form of this key contains any deny-listed term. */
  static boolean namesASecret(String key) {
    String canonical = canonical(key);
    return TERMS.stream().anyMatch(canonical::contains);
  }

  /**
   * Lower case with diacritics folded away — {@code contraseña}, {@code contrasena} and the
   * decomposed {@code contraseña} a Mac filesystem hands back all become one string. Separators
   * are deliberately NOT stripped, which is why {@code api_key} and {@code apikey} are two terms
   * above rather than one.
   */
  static String canonical(String text) {
    String lower = text.toLowerCase(java.util.Locale.ROOT);
    return COMBINING_MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
  }
}
