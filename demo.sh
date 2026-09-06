#!/usr/bin/env bash
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# NarrativeTrace demo launcher — one command, the trace story front and center.
#
#   ./demo.sh                          interactive example picker
#   ./demo.sh --example ecommerce      non-interactive
#   ./demo.sh --example ecommerce --classic   traditional timestamped logs, no demo styling
#   ./demo.sh --example ecommerce --no-pause  play straight through, no stop points
#   ./demo.sh --list                   list available examples
#
# On a terminal the demo stops after each scenario — [Enter] continues, q quits — and
# every scenario carries a note on how its trace is configured (demo/wiring.awk).
# --lang es|zh-CN re-renders the same run through the example's committed glossary
# (glossary.json): identifiers get glossed, values stay byte-identical, and untranslated
# phrases land in a "glossary gaps" footer. The picker lists the locales the glossary
# actually carries.
set -euo pipefail
cd "$(dirname "$0")"

EXAMPLES=(ecommerce clarity minecraft library)
DEMO_DIR="narrativetrace-examples/demo"
example=""
lang="en"
lang_set=0
classic=0
no_pause=0

usage() {
  # 2..15 is the header comment block; 16 is `set -euo pipefail`, which the
  # old upper bound printed as the last line of --help.
  sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -e|--example) example="${2:?--example needs a value}"; shift 2 ;;
    --lang) lang="${2:?--lang needs a value}"; lang_set=1; shift 2 ;;
    --classic) classic=1; shift ;;
    --no-pause) no_pause=1; shift ;;
    --list) printf '%s\n' "${EXAMPLES[@]}"; exit 0 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

is_valid_example() {
  local candidate
  for candidate in "${EXAMPLES[@]}"; do
    [[ "$candidate" == "$1" ]] && return 0
  done
  return 1
}

if [[ -z "$example" ]]; then
  if [[ ! -t 0 ]]; then
    echo "stdin is not a terminal: pass --example NAME (see --list)" >&2
    exit 2
  fi
  echo "Which example? (the trace narrates as the code runs)"
  select example in "${EXAMPLES[@]}"; do
    [[ -n "$example" ]] && break
  done
fi

if ! is_valid_example "$example"; then
  echo "unknown example: $example (try --list)" >&2
  exit 2
fi

# Languages come from the example's committed glossary (glossary.json): a locale is
# offered only when at least one term carries a translation for it. The candidate set
# matches the scaffolding bundles the library ships (es, zh-CN) — other locales would
# render with English scaffolding and an all-gaps footer, which is not a demo.
glossary_file="narrativetrace-examples/$example/glossary.json"
supported_langs() {
  echo en
  [[ -f "$glossary_file" ]] || return 0
  local candidate
  for candidate in es zh-CN; do
    grep -q "\"$candidate\":" "$glossary_file" && echo "$candidate"
  done
}

# Array-building loops instead of mapfile: macOS ships bash 3.2 (no mapfile), and
# `#!/usr/bin/env bash` resolves to it — the demo must run on a stock Mac.
collect_langs() {
  langs=()
  local l
  while IFS= read -r l; do langs+=("$l"); done < <(supported_langs)
}

if [[ "$lang_set" == 0 && -t 0 && "$classic" == 0 ]]; then
  collect_langs
  if [[ "${#langs[@]}" -gt 1 ]]; then
    echo
    echo "Which language? / ¿Qué idioma? / 哪种语言?(the SAME run re-rendered via the example's glossary)"
    select lang in "${langs[@]}"; do
      [[ -n "$lang" ]] && break
    done
  fi
fi

# Membership test via the collected list — piping supported_langs into `grep -q`
# would SIGPIPE the producer under pipefail and misreport valid locales.
collect_langs
lang_ok=0
for candidate in "${langs[@]}"; do
  [[ "$candidate" == "$lang" ]] && lang_ok=1
done
if [[ "$lang_ok" == 0 ]]; then
  echo "language '$lang' is not in $example's glossary (supported: ${langs[*]})" >&2
  exit 2
fi
if [[ "$lang" != "en" && "$classic" == 1 ]]; then
  echo "--classic replays raw logback output; it has no translated variant. Drop one of the flags." >&2
  exit 2
fi

# Localized launcher chrome for translated runs — the demo speaks the chosen language,
# not just the traces. Plain variables per locale (macOS bash 3.2: no associative
# arrays). en never reads these: the English path below has its own prose. Scenario
# titles and identifiers stay in the original language, like the translated views.
case "$lang" in
  es)
    chrome_running="Ejecutando %s (renderizado en '%s' sobre la marcha)..."
    chrome_ran="✔ %s ejecutado; traducido vía %s."
    chrome_failed="✖ %s falló:"
    chrome_intro1="La misma ejecución, re-renderizada en '%s' desde %s."
    chrome_intro2="Los identificadores traducidos conservan el original entre paréntesis para poder seguir buscando en el log canónico;"
    chrome_intro3="el pie 'Vacíos del glosario' lista las frases que el glosario aún no cubre — esa lista ES la cola de trabajo de curación."
    chrome_intro4="Los nombres genéricos (minecraft sin refactorizar, clarity legacy) quedan sin traducir a propósito:"
    chrome_intro5="un nombre que no cuenta ninguna historia no puede traducirse en una."
    chrome_no_traces="no se produjeron trazas traducidas"
    chrome_pause="[Intro] continúa, q sale"
    chrome_tip="Consejo: ./demo.sh --example %s compara esto con la ejecución en inglés."
    ;;
  zh-CN)
    chrome_running="正在运行 %s(以 '%s' 实时渲染)..."
    chrome_ran="✔ %s 运行完毕,已通过 %s 翻译。"
    chrome_failed="✖ %s 运行失败:"
    chrome_intro1="同一次运行,已按 '%s' 经 %s 重新渲染。"
    chrome_intro2="翻译后的标识符在括号中保留原文,规范日志仍可用 grep 检索;"
    chrome_intro3="\"术语表缺口\"页脚列出术语表尚未覆盖的短语 — 那份清单就是词汇整理的工作队列。"
    chrome_intro4="泛化的名字(minecraft 未重构版、clarity legacy)特意保持不译:"
    chrome_intro5="讲不出故事的名字,也翻译不出故事。"
    chrome_no_traces="没有生成任何翻译后的追踪"
    chrome_pause="[回车] 继续,q 退出"
    chrome_tip="提示:./demo.sh --example %s 可与英文运行对比。"
    ;;
esac

# Stop points need a terminal to read the key from and a viewer to press it: never for
# pipes/CI, never for --classic (that mode is deliberately one verbatim wall of logs).
pause=0
if [[ "$no_pause" == 0 && "$classic" == 0 && -t 0 && -t 1 ]]; then
  pause=1
fi

# Waits for a background child, animating a braille spinner on a TTY (pipes and CI just
# wait quietly). Ctrl-C kills the child rather than orphaning it. Returns the child's
# status so callers can show the buffered output on failure.
await() {
  local pid="$1" message="$2" frames='⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏' i=0
  trap 'kill "$pid" 2>/dev/null; printf "\n"; exit 130' INT
  if [[ -t 1 ]]; then
    while kill -0 "$pid" 2>/dev/null; do
      printf '\r%s %s' "${frames:i++%10:1}" "$message"
      sleep 0.1
    done
  fi
  trap - INT
  wait "$pid"
}

# Build output is buffered so a failure still shows the real Gradle error.
build_example() {
  local target=":narrativetrace-examples:$example:installDist" log
  if [[ ! -t 1 ]]; then
    echo "Building $example (quiet, one-time)..."
    ./gradlew -q --console=plain "$target"
    return
  fi
  log=$(mktemp)
  ./gradlew -q --console=plain "$target" >"$log" 2>&1 &
  if await $! "Building $example (quiet, one-time)..."; then
    printf '\r✔ %s built.                                                  \n' "$example"
    rm -f "$log"
  else
    printf '\r✖ Build failed for %s:                                       \n' "$example"
    cat "$log"
    rm -f "$log"
    exit 1
  fi
}
build_example

# Presentation lives in demo/colorize.awk (styling, wiring notes, stop points) with the
# per-scenario prose in demo/wiring.awk. NO_COLOR strips the colors, keeps the structure.
colorize() {
  awk -v use_color="$1" -v pause="$2" -f "$DEMO_DIR/wiring.awk" -f "$DEMO_DIR/colorize.awk"
}

legend() {
  local c="$1" cyan="" green="" red="" dim="" reset=""
  if [[ "$c" == 1 ]]; then
    cyan=$'\033[36m'; green=$'\033[32m'; red=$'\033[1;31m'; dim=$'\033[2m'; reset=$'\033[0m'
  fi
  cat <<EOF

${dim}One recording, many views — every section below is the SAME captured trace, re-rendered:${reset}

  ${cyan}→ method entered${reset}   ${green}← returned${reset}   ${red}!! exception${reset}   live, as the code runs; indent = call depth

  tree     structure, values, and timings; // lines are @Narrated templates

  prose    the trace as English sentences — derived from your class and method names

  diagram  Mermaid markup, plus an ASCII sequence diagram drawn by PlantUML

${dim}Each scenario opens with how its trace is configured. No logging code was written for any of it.${reset}

EOF
}

runner="narrativetrace-examples/$example/build/install/$example/bin/$example"

# --lang es|zh-CN: the run renders its own translated scenario files in-process —
# DemoTraces sends each scenario's trace through the example's committed glossary
# (the same rendering the live TranslationSubscriber uses) — and the launcher walks
# them afterwards. Values, return values, and exception messages are byte-identical
# to the English run — only glossary-covered identifiers and templates change.
if [[ "$lang" != "en" ]]; then
  color=1
  [[ -n "${NO_COLOR:-}" || ! -t 1 ]] && color=0
  cyan=""; dim=""; reset=""
  if [[ "$color" == 1 ]]; then cyan=$'\033[36m'; dim=$'\033[2m'; reset=$'\033[0m'; fi

  work=$(mktemp -d)
  trap 'rm -rf "$work"' EXIT
  export JAVA_OPTS="-Dnarrativetrace.demo.translationDir=$work/traces-$lang -Dnarrativetrace.demo.locale=$lang -Dnarrativetrace.glossary.path=$PWD/$glossary_file -Dlogback.configurationFile=$PWD/$DEMO_DIR/logback-demo.xml"
  run_log=""
  capture_run_quiet() {
    local running_msg
    printf -v running_msg "$chrome_running" "$example" "$lang"
    run_log=$(mktemp -p "$work")
    "$runner" >"$run_log" 2>&1 &
    if await $! "$running_msg"; then
      printf "\r$chrome_ran                                \n" "$example" "$glossary_file"
    else
      printf "\r$chrome_failed                                                 \n" "$example"
      cat "$run_log"
      exit 1
    fi
  }
  capture_run_quiet

  # The wiring note for one scenario, localized: table lookup by the exact title that
  # DemoTraces wrote as the file's first line (print-wiring.awk handles the async
  # per-thread title variants).
  wiring_note() {
    local noteline
    awk -v key="$1" -f "$DEMO_DIR/wiring-$lang.awk" -f "$DEMO_DIR/print-wiring.awk" </dev/null |
      while IFS= read -r noteline; do printf '%s\n' "${dim}    $noteline${reset}"; done
  }

  echo
  printf "${dim}$chrome_intro1${reset}\n" "$lang" "$glossary_file"
  echo "${dim}$chrome_intro2${reset}"
  echo "${dim}$chrome_intro3${reset}"
  echo "${dim}$chrome_intro4${reset}"
  echo "${dim}$chrome_intro5${reset}"
  for f in "$work/traces-$lang"/*.md; do
    [[ -e "$f" ]] || { echo "$chrome_no_traces" >&2; exit 1; }
    title=$(head -n 1 "$f" | sed 's/^=== //; s/ ===$//')
    echo
    echo "${cyan}=== $title ===${reset}"
    wiring_note "$title"
    echo
    tail -n +3 "$f"
    if [[ "$pause" == 1 ]]; then
      printf '%s' "${dim}$chrome_pause${reset} "
      IFS= read -r key </dev/tty || key=""
      if [[ "$key" == q* ]]; then break; fi
      printf '\033[1A\033[2K'
    fi
  done
  echo
  printf "$chrome_tip\n" "$example"
  exit 0
fi

# --classic: run with the example's own bundled logback config — the traditional
# format every log tool shows (date, time, level, thread, logger). Verbatim, no
# demo styling: the point is that these are ordinary logs through ordinary logback.
if [[ "$classic" == 1 ]]; then
  echo "Classic log format: same run, the example's own logback config — date, level, [thread], [logger]."
  echo
  exec "$runner"
fi

# Start scripts place JAVA_OPTS after their default JVM args; for duplicated -D
# system properties the last occurrence wins, so this overrides the example's
# bundled logback config with the demo pattern.
export JAVA_OPTS="-Dlogback.configurationFile=$PWD/$DEMO_DIR/logback-demo.xml"

# A paced run is recorded first, then walked. Streaming into a reader that waits for a
# human fills the pipe and blocks the JVM mid-scenario, inflating the very durations the
# trace tree reports — the run must finish unobserved for its timings to mean anything.
# It also keeps quitting clean: no live pipe, so no SIGPIPE to unwind.
run_log=""
capture_run() {
  run_log=$(mktemp)
  "$runner" >"$run_log" 2>&1 &
  if await $! "Running $example (recorded in full, so the timings stay honest)..."; then
    printf '\r✔ %s ran. Walking it scenario by scenario.                   \n' "$example"
  else
    printf '\r✖ %s failed:                                                 \n' "$example"
    cat "$run_log"
    rm -f "$run_log"
    exit 1
  fi
}

# On a terminal (or FORCE_COLOR=1, e.g. for VHS): legend + structured, colorized
# stream; NO_COLOR keeps the structure but drops the colors. Pipes and CI get the
# verbatim example output via exec.
if [[ -n "${FORCE_COLOR:-}" || -t 1 ]]; then
  color=1
  [[ -n "${NO_COLOR:-}" ]] && color=0
  legend "$color"
  if [[ "$pause" == 1 ]]; then
    capture_run
    colorize "$color" 1 <"$run_log"
    rm -f "$run_log"
  else
    "$runner" | colorize "$color" 0
  fi
  echo
  echo "Tip: ./demo.sh --example $example --classic replays this as traditional timestamped logs."
  if [[ "$pause" == 1 ]]; then
    echo "     ./demo.sh --example $example --no-pause plays it straight through."
  fi
else
  exec "$runner"
fi
