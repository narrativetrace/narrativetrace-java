/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static ai.narrativetrace.core.render.SensitiveConcept.Category.CREDENTIAL;
import static ai.narrativetrace.core.render.SensitiveConcept.Category.NATIONAL_ID;
import static ai.narrativetrace.core.render.SensitiveConcept.Category.PAYMENT;
import static ai.narrativetrace.core.render.SensitiveConcept.Category.SESSION;
import static ai.narrativetrace.core.render.SensitiveConcept.Language.DE;
import static ai.narrativetrace.core.render.SensitiveConcept.Language.EN;
import static ai.narrativetrace.core.render.SensitiveConcept.Language.ES;
import static ai.narrativetrace.core.render.SensitiveConcept.Language.FR;
import static ai.narrativetrace.core.render.SensitiveConcept.Language.PT;
import static ai.narrativetrace.core.render.SensitiveConcept.Language.ZH;
import static ai.narrativetrace.core.render.SensitiveConcept.Term.of;
import static ai.narrativetrace.core.render.SensitiveConcept.Term.token;

import ai.narrativetrace.core.render.SensitiveConcept.ValueShape.Detected;
import ai.narrativetrace.core.render.SensitiveConcept.ValueShape.None;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The built-in vocabulary of sensitive concepts — the floor beneath every redaction decision.
 *
 * <p>INTENT: One entry per concept, so that what a concept is <em>called</em>, in which languages,
 * and what it <em>looks like</em> are three facts held together rather than three unrelated
 * decisions nobody has to make at the same time. The previous flat list of strings had no place to
 * say "US SSN is covered by name in English only, and by no value shape at all", which is precisely
 * why it stayed that way.
 *
 * <p><b>@llmNote</b> Read at class-initialisation time only. {@link RedactionPolicy} flattens this
 * into its two lookup sets once; no traced call ever walks this list. Its size — including every
 * language added in future — has no steady-state cost.
 *
 * <p><b>@edgeCase</b> Underscore and non-underscore spellings are separate terms on purpose. {@link
 * SecretValueShapes#canonical} lower-cases and folds accents but does not strip separators, so
 * {@code api_key} and {@code apikey} are two words as far as matching is concerned.
 */
final class SensitiveVocabulary {

  private SensitiveVocabulary() {}

  /**
   * Every concept the library redacts by name, and how each is recognised by value.
   *
   * <p>Adding a concept means answering all three questions the record asks. Adding a language
   * means filling that language's column across the concepts where it differs from English — {@code
   * SensitiveVocabularyCoverageTest} reports which are still empty.
   */
  static final List<SensitiveConcept> CONCEPTS =
      List.of(
          new SensitiveConcept(
              "password",
              CREDENTIAL,
              Map.of(
                  EN, Set.of(of("password"), of("passwd"), of("passphrase")),
                  ES,
                      Set.of(
                          of("contrasena"),
                          of("claveacceso"),
                          of("clave_acceso"),
                          of("clavesecreta"),
                          of("clave_secreta")),
                  // token-matched: 'senha' sits inside chosenHash and frozenHash across the
                  // camel-case seam, so a substring match would blank ordinary fields
                  PT, Set.of(token("senha")),
                  FR, Set.of(of("motdepasse"), of("mot_de_passe")),
                  DE, Set.of(of("passwort"), of("kennwort")),
                  // token-matched: 'mima' sits inside semiMajorAxis once case is folded away
                  ZH, Set.of(of("密码"), token("mima"))),
              new None("a password is any string at all — it has no shape to recognise")),
          new SensitiveConcept(
              "api-token",
              CREDENTIAL,
              Map.of(
                  EN,
                  Set.of(
                      of("token"),
                      of("apikey"),
                      of("api_key"),
                      of("accesskey"),
                      of("access_key"),
                      of("bearer"))),
              new None(
                  "a JWT-shaped token is caught by the jwt shape, but an opaque API key is"
                      + " indistinguishable from any other identifier")),
          new SensitiveConcept(
              "generic-secret",
              CREDENTIAL,
              Map.of(EN, Set.of(of("secret"), of("credential"))),
              new None("'secret' is a name for anything confidential, not a format")),
          new SensitiveConcept(
              "private-key",
              CREDENTIAL,
              Map.of(EN, Set.of(of("privatekey"), of("private_key"))),
              new None("PEM header shape not implemented yet — see the redaction wave in TODO")),
          new SensitiveConcept(
              "authorization-header",
              CREDENTIAL,
              Map.of(EN, Set.of(of("authorization"))),
              new None(
                  "the jwt shape matches a bare JWT, not a 'Bearer <jwt>' header value that"
                      + " carries one")),
          new SensitiveConcept(
              "one-time-code",
              CREDENTIAL,
              Map.of(EN, Set.of(of("otp"), of("mfacode"), of("mfa_code"), of("totp"))),
              new None("a six-digit code is indistinguishable from any other short number")),
          new SensitiveConcept(
              "session-identifier",
              SESSION,
              Map.of(EN, Set.of(of("sessionid"), of("session_id"))),
              new None("session ids are opaque and format-free by design")),
          new SensitiveConcept(
              "cookie",
              SESSION,
              Map.of(EN, Set.of(of("cookie"), of("setcookie"), of("set_cookie"))),
              new Detected("set-cookie")),
          new SensitiveConcept("jwt", SESSION, Map.of(EN, Set.of(of("jwt"))), new Detected("jwt")),
          new SensitiveConcept(
              "payment-card-number",
              PAYMENT,
              Map.of(
                  EN, Set.of(of("cardnumber"), of("card_number"), token("pan")),
                  ES, Set.of(of("tarjeta")),
                  PT, Set.of(of("cartao")),
                  FR,
                      Set.of(
                          of("cartebancaire"),
                          of("carte_bancaire"),
                          of("numerocarte"),
                          of("numero_carte"))),
              new Detected("luhn-pan")),
          new SensitiveConcept(
              "card-verification-value",
              PAYMENT,
              Map.of(EN, Set.of(of("cvv"))),
              new None("three digits is indistinguishable from any other small number")),
          new SensitiveConcept(
              "bank-account-number",
              PAYMENT,
              Map.of(
                  EN,
                  Set.of(
                      of("accountnumber"),
                      of("account_number"),
                      of("routingnumber"),
                      of("routing_number"),
                      token("iban"))),
              new None("IBAN has a checksum and should gain a shape — see TODO redaction wave")),
          new SensitiveConcept(
              "us-social-security-number",
              NATIONAL_ID,
              Map.of(
                  EN,
                  Set.of(
                      of("ssn"),
                      of("socialsecurity"),
                      of("social_security"),
                      of("socialsecuritynumber"),
                      of("taxid"),
                      of("tax_id"))),
              // The audit's headline gap, now closed on both axes. Only the dashed form is
              // recognised by value: a US SSN carries no checksum, so bare nine digits would blank
              // every order number ever written.
              new Detected("us-ssn-dashed")),
          new SensitiveConcept(
              "chilean-rut", NATIONAL_ID, Map.of(ES, Set.of(token("rut"))), new Detected("rut")),
          new SensitiveConcept(
              "argentine-cuit",
              NATIONAL_ID,
              Map.of(ES, Set.of(token("cuit"))),
              new None(
                  "CUIT carries a checksum and NationalIdShapes does not implement it — the name"
                      + " is covered, the value is not")),
          new SensitiveConcept(
              "spanish-national-id",
              NATIONAL_ID,
              Map.of(ES, Set.of(token("dni"), of("cedula"))),
              new Detected("spanish-dni-nie")),
          new SensitiveConcept(
              "brazilian-cpf-cnpj",
              NATIONAL_ID,
              Map.of(PT, Set.of(token("cpf"), token("cnpj"))),
              new Detected("cpf-cnpj")),
          new SensitiveConcept(
              "french-nir",
              NATIONAL_ID,
              Map.of(FR, Set.of(token("nir"))),
              new Detected("french-nir")),
          new SensitiveConcept(
              "chinese-resident-id",
              NATIONAL_ID,
              Map.of(ZH, Set.of(of("身份证"), of("shenfenzheng"))),
              new Detected("chinese-resident-id")));

  /** Terms matched anywhere inside an identifier. */
  static Set<String> substringTerms() {
    return termsMatching(SensitiveConcept.MatchMode.SUBSTRING);
  }

  /** Terms matched only against whole identifier tokens. */
  static Set<String> identifierTokenTerms() {
    return termsMatching(SensitiveConcept.MatchMode.IDENTIFIER_TOKEN);
  }

  private static Set<String> termsMatching(SensitiveConcept.MatchMode mode) {
    return CONCEPTS.stream()
        .flatMap(concept -> concept.allTerms().stream())
        .filter(term -> term.match() == mode)
        .map(SensitiveConcept.Term::text)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }
}
