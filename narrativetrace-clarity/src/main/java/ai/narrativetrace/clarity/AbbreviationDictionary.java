/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import java.util.Locale;
import java.util.Map;

/**
 * Dictionary of common abbreviations that reduce naming clarity (e.g., mgr, impl, util).
 *
 * <p>A project accepts its own shorthand by committing the word to its glossary: a token in the
 * {@link DomainVocabulary} is the project's word, not an abbreviation of someone else's; see {@link
 * #lookup}.
 */
public final class AbbreviationDictionary {

  public enum Tier {
    UNIVERSAL,
    WELL_KNOWN,
    AMBIGUOUS
  }

  public record Entry(Tier tier, double score, String expansion) {}

  private static final Map<String, Entry> ABBREVIATIONS =
      Map.ofEntries(
          // Universal (0.8) — universally understood
          entry("id", Tier.UNIVERSAL, "identifier"),
          entry("url", Tier.UNIVERSAL, "uniform resource locator"),
          entry("uri", Tier.UNIVERSAL, "uniform resource identifier"),
          entry("api", Tier.UNIVERSAL, "application programming interface"),
          entry("html", Tier.UNIVERSAL, "hypertext markup language"),
          entry("xml", Tier.UNIVERSAL, "extensible markup language"),
          entry("json", Tier.UNIVERSAL, "javascript object notation"),
          entry("http", Tier.UNIVERSAL, "hypertext transfer protocol"),
          entry("https", Tier.UNIVERSAL, "hypertext transfer protocol secure"),
          entry("db", Tier.UNIVERSAL, "database"),
          entry("io", Tier.UNIVERSAL, "input output"),
          entry("ui", Tier.UNIVERSAL, "user interface"),
          entry("ok", Tier.UNIVERSAL, "okay"),
          entry("max", Tier.UNIVERSAL, "maximum"),
          entry("min", Tier.UNIVERSAL, "minimum"),
          entry("sql", Tier.UNIVERSAL, "structured query language"),
          entry("css", Tier.UNIVERSAL, "cascading style sheets"),
          entry("tcp", Tier.UNIVERSAL, "transmission control protocol"),
          entry("udp", Tier.UNIVERSAL, "user datagram protocol"),
          entry("ip", Tier.UNIVERSAL, "internet protocol"),
          entry("dns", Tier.UNIVERSAL, "domain name system"),
          entry("ssh", Tier.UNIVERSAL, "secure shell"),
          entry("ssl", Tier.UNIVERSAL, "secure sockets layer"),
          entry("tls", Tier.UNIVERSAL, "transport layer security"),
          entry("jwt", Tier.UNIVERSAL, "json web token"),
          entry("cpu", Tier.UNIVERSAL, "central processing unit"),
          entry("gpu", Tier.UNIVERSAL, "graphics processing unit"),
          entry("ram", Tier.UNIVERSAL, "random access memory"),
          entry("os", Tier.UNIVERSAL, "operating system"),
          entry("jvm", Tier.UNIVERSAL, "java virtual machine"),
          entry("gc", Tier.UNIVERSAL, "garbage collector"),
          entry("uuid", Tier.UNIVERSAL, "universally unique identifier"),
          entry("sdk", Tier.UNIVERSAL, "software development kit"),
          entry("grpc", Tier.UNIVERSAL, "grpc remote procedure calls"),
          entry("sso", Tier.UNIVERSAL, "single sign on"),
          entry("mfa", Tier.UNIVERSAL, "multi factor authentication"),
          entry("pii", Tier.UNIVERSAL, "personally identifiable information"),
          entry("kpi", Tier.UNIVERSAL, "key performance indicator"),

          // Well-known (0.6) — understood by most developers
          entry("ctx", Tier.WELL_KNOWN, "context"),
          entry("cfg", Tier.WELL_KNOWN, "configuration"),
          entry("config", Tier.WELL_KNOWN, "configuration"),
          entry("mgr", Tier.WELL_KNOWN, "manager"),
          entry("impl", Tier.WELL_KNOWN, "implementation"),
          entry("src", Tier.WELL_KNOWN, "source"),
          entry("dest", Tier.WELL_KNOWN, "destination"),
          entry("dst", Tier.WELL_KNOWN, "destination"),
          entry("buf", Tier.WELL_KNOWN, "buffer"),
          entry("idx", Tier.WELL_KNOWN, "index"),
          entry("tmp", Tier.WELL_KNOWN, "temporary"),
          entry("temp", Tier.WELL_KNOWN, "temporary"),
          entry("auth", Tier.WELL_KNOWN, "authentication"),
          entry("repo", Tier.WELL_KNOWN, "repository"),
          entry("env", Tier.WELL_KNOWN, "environment"),
          entry("async", Tier.WELL_KNOWN, "asynchronous"),
          entry("sync", Tier.WELL_KNOWN, "synchronous"),
          entry("param", Tier.WELL_KNOWN, "parameter"),
          entry("params", Tier.WELL_KNOWN, "parameters"),
          entry("attr", Tier.WELL_KNOWN, "attribute"),
          entry("attrs", Tier.WELL_KNOWN, "attributes"),
          entry("ref", Tier.WELL_KNOWN, "reference"),
          entry("conn", Tier.WELL_KNOWN, "connection"),
          entry("stmt", Tier.WELL_KNOWN, "statement"),
          entry("msg", Tier.WELL_KNOWN, "message"),
          entry("req", Tier.WELL_KNOWN, "request"),
          entry("res", Tier.WELL_KNOWN, "response"),
          entry("resp", Tier.WELL_KNOWN, "response"),
          entry("err", Tier.WELL_KNOWN, "error"),
          entry("exc", Tier.WELL_KNOWN, "exception"),
          entry("ex", Tier.WELL_KNOWN, "exception"),
          entry("cmd", Tier.WELL_KNOWN, "command"),
          entry("arg", Tier.WELL_KNOWN, "argument"),
          entry("args", Tier.WELL_KNOWN, "arguments"),
          entry("val", Tier.WELL_KNOWN, "value"),
          entry("var", Tier.WELL_KNOWN, "variable"),
          entry("vars", Tier.WELL_KNOWN, "variables"),
          entry("str", Tier.WELL_KNOWN, "string"),
          entry("num", Tier.WELL_KNOWN, "number"),
          entry("len", Tier.WELL_KNOWN, "length"),
          entry("pos", Tier.WELL_KNOWN, "position"),
          entry("prev", Tier.WELL_KNOWN, "previous"),
          entry("cur", Tier.WELL_KNOWN, "current"),
          entry("curr", Tier.WELL_KNOWN, "current"),
          entry("iter", Tier.WELL_KNOWN, "iterator"),
          entry("obj", Tier.WELL_KNOWN, "object"),
          entry("fn", Tier.WELL_KNOWN, "function"),
          entry("func", Tier.WELL_KNOWN, "function"),
          entry("cb", Tier.WELL_KNOWN, "callback"),
          entry("evt", Tier.WELL_KNOWN, "event"),
          entry("elem", Tier.WELL_KNOWN, "element"),
          entry("elems", Tier.WELL_KNOWN, "elements"),
          entry("prop", Tier.WELL_KNOWN, "property"),
          entry("props", Tier.WELL_KNOWN, "properties"),
          entry("dir", Tier.WELL_KNOWN, "directory"),
          entry("lib", Tier.WELL_KNOWN, "library"),
          entry("pkg", Tier.WELL_KNOWN, "package"),
          entry("ver", Tier.WELL_KNOWN, "version"),
          entry("doc", Tier.WELL_KNOWN, "document"),
          entry("docs", Tier.WELL_KNOWN, "documents"),
          entry("spec", Tier.WELL_KNOWN, "specification"),
          entry("fmt", Tier.WELL_KNOWN, "format"),
          entry("seq", Tier.WELL_KNOWN, "sequence"),
          entry("init", Tier.WELL_KNOWN, "initialize"),
          entry("dyn", Tier.WELL_KNOWN, "dynamic"),
          entry("alloc", Tier.WELL_KNOWN, "allocate"),
          entry("dealloc", Tier.WELL_KNOWN, "deallocate"),
          entry("chan", Tier.WELL_KNOWN, "channel"),
          entry("ack", Tier.WELL_KNOWN, "acknowledge"),
          entry("nack", Tier.WELL_KNOWN, "negative acknowledge"),
          entry("coll", Tier.WELL_KNOWN, "collection"),
          entry("desc", Tier.WELL_KNOWN, "description"),
          entry("info", Tier.WELL_KNOWN, "information"),
          entry("stat", Tier.WELL_KNOWN, "status"),
          entry("stats", Tier.WELL_KNOWN, "statistics"),
          entry("cnt", Tier.WELL_KNOWN, "count"),
          entry("avg", Tier.WELL_KNOWN, "average"),
          entry("pct", Tier.WELL_KNOWN, "percent"),
          entry("delim", Tier.WELL_KNOWN, "delimiter"),
          entry("sep", Tier.WELL_KNOWN, "separator"),
          entry("hdr", Tier.WELL_KNOWN, "header"),
          entry("svc", Tier.WELL_KNOWN, "service"),
          entry("txn", Tier.WELL_KNOWN, "transaction"),
          entry("tx", Tier.WELL_KNOWN, "transaction"),
          entry("ttl", Tier.WELL_KNOWN, "time to live"),
          entry("qps", Tier.WELL_KNOWN, "queries per second"),
          entry("rps", Tier.WELL_KNOWN, "requests per second"),
          entry("mtls", Tier.WELL_KNOWN, "mutual transport layer security"),
          entry("oidc", Tier.WELL_KNOWN, "openid connect"),
          entry("sli", Tier.WELL_KNOWN, "service level indicator"),
          entry("slo", Tier.WELL_KNOWN, "service level objective"),
          entry("sla", Tier.WELL_KNOWN, "service level agreement"),
          entry("etl", Tier.WELL_KNOWN, "extract transform load"),
          entry("elt", Tier.WELL_KNOWN, "extract load transform"),
          entry("cdc", Tier.WELL_KNOWN, "change data capture"),
          entry("oltp", Tier.WELL_KNOWN, "online transaction processing"),
          entry("olap", Tier.WELL_KNOWN, "online analytical processing"),
          entry("p99", Tier.WELL_KNOWN, "99th percentile"),
          entry("llm", Tier.WELL_KNOWN, "large language model"),
          entry("asr", Tier.WELL_KNOWN, "automatic speech recognition"),
          entry("tts", Tier.WELL_KNOWN, "text to speech"),
          entry("ner", Tier.WELL_KNOWN, "named entity recognition"),
          entry("ocr", Tier.WELL_KNOWN, "optical character recognition"),
          entry("totp", Tier.WELL_KNOWN, "time based one time password"),
          entry("siem", Tier.WELL_KNOWN, "security information and event management"),
          entry("soc2", Tier.WELL_KNOWN, "service organization control 2"),
          entry("gdpr", Tier.WELL_KNOWN, "general data protection regulation"),
          entry("hipaa", Tier.WELL_KNOWN, "health insurance portability and accountability act"),

          // Ambiguous (0.3) — multiple possible meanings
          entry("cust", Tier.AMBIGUOUS, "customer or custom"),
          entry("proc", Tier.AMBIGUOUS, "process"),
          entry("mod", Tier.AMBIGUOUS, "module or modifier"),
          entry("del", Tier.AMBIGUOUS, "delete or delegate"),
          entry("acc", Tier.AMBIGUOUS, "account or accumulator"),
          entry("op", Tier.AMBIGUOUS, "operation or operator"),
          entry("rec", Tier.AMBIGUOUS, "record or receiver"),
          entry("sec", Tier.AMBIGUOUS, "section or security or second"),
          entry("gen", Tier.AMBIGUOUS, "generate or generic or generation"),
          entry("app", Tier.AMBIGUOUS, "application or append"),
          entry("calc", Tier.AMBIGUOUS, "calculate or calculation"),
          entry("cat", Tier.AMBIGUOUS, "category or concatenate"),
          entry("comp", Tier.AMBIGUOUS, "compare or component or compute"),
          entry("loc", Tier.AMBIGUOUS, "location or locale"),
          entry("perm", Tier.AMBIGUOUS, "permission or permanent"),
          entry("reg", Tier.AMBIGUOUS, "register or regular or registry"),
          entry("srv", Tier.AMBIGUOUS, "server or service"),
          entry("tgt", Tier.AMBIGUOUS, "target"),
          entry("hdl", Tier.AMBIGUOUS, "handle or handler"),
          entry("blk", Tier.AMBIGUOUS, "block"),
          entry("chk", Tier.AMBIGUOUS, "check"),
          entry("clr", Tier.AMBIGUOUS, "clear or color"),
          entry("cmp", Tier.AMBIGUOUS, "compare or component"),
          entry("cpy", Tier.AMBIGUOUS, "copy"),
          entry("dup", Tier.AMBIGUOUS, "duplicate"),
          entry("flt", Tier.AMBIGUOUS, "filter or float"),
          entry("grp", Tier.AMBIGUOUS, "group"),
          entry("lbl", Tier.AMBIGUOUS, "label"),
          entry("lvl", Tier.AMBIGUOUS, "level"),
          entry("mgmt", Tier.AMBIGUOUS, "management"),
          entry("neg", Tier.AMBIGUOUS, "negative or negate"),
          entry("orig", Tier.AMBIGUOUS, "original or origin"),
          entry("pfx", Tier.AMBIGUOUS, "prefix"),
          entry("sfx", Tier.AMBIGUOUS, "suffix"),
          entry("sig", Tier.AMBIGUOUS, "signal or signature"),
          entry("sym", Tier.AMBIGUOUS, "symbol or symmetric"),
          entry("tbl", Tier.AMBIGUOUS, "table"),
          entry("tok", Tier.AMBIGUOUS, "token"),
          entry("usr", Tier.AMBIGUOUS, "user"),
          entry("wgt", Tier.AMBIGUOUS, "weight"),
          entry("rag", Tier.AMBIGUOUS, "retrieval augmented generation or red amber green"));

  private static Map.Entry<String, Entry> entry(String abbr, Tier tier, String expansion) {
    return Map.entry(abbr, new Entry(tier, tierScore(tier), expansion));
  }

  private static double tierScore(Tier tier) {
    return switch (tier) {
      case UNIVERSAL -> 0.8;
      case WELL_KNOWN -> 0.6;
      case AMBIGUOUS -> 0.3;
    };
  }

  private final DomainVocabulary vocabulary;

  /** A dictionary with no project vocabulary — every built-in abbreviation is penalized. */
  public AbbreviationDictionary() {
    this(DomainVocabulary.empty());
  }

  /**
   * @param vocabulary the project's declared vocabulary; must not be {@code null}
   */
  public AbbreviationDictionary(DomainVocabulary vocabulary) {
    if (vocabulary == null) {
      throw new IllegalArgumentException("vocabulary must not be null");
    }
    this.vocabulary = vocabulary;
  }

  /**
   * Looks one token up as an abbreviation.
   *
   * <p><b>@edgeCase</b> A token listed in the project glossary's {@code abbreviations} section
   * returns {@code null} — the same answer as a word the dictionary never knew. Callers already
   * treat {@code null} as "not an abbreviation", so accepted shorthand is neither scored down nor
   * asked to be spelled out. Note what does <em>not</em> qualify: appearing as a token inside a
   * committed phrase. Committing {@code calc total} accepts the phrase, not {@code calc}.
   *
   * @param token identifier token to look up; must not be {@code null}
   * @return the entry, or {@code null} when the token is not a penalized abbreviation
   */
  public Entry lookup(String token) {
    var lower = token.toLowerCase(Locale.ROOT);
    return vocabulary.isAcceptedAbbreviation(lower) ? null : ABBREVIATIONS.get(lower);
  }

  /**
   * Returns what the project declared this accepted abbreviation stands for.
   *
   * <p>INTENT: The teaching half of acceptance. {@link #lookup(String)} goes silent on accepted
   * shorthand so it is not penalized; this method gives the note channel something to say instead
   * of nothing — {@code fx → foreign exchange}, in the project's own words.
   *
   * @param token identifier token; must not be {@code null}
   * @return the declared expansion, or {@code null} when the project declared no such shorthand
   */
  public String projectExpansionOf(String token) {
    return vocabulary.expansionOf(token.toLowerCase(Locale.ROOT));
  }
}
