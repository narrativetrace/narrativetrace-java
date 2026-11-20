# narrativetrace-java

The NarrativeTrace agent skills for Java. This plugin carries 7 skills and nothing else: no hooks, no MCP servers, no executables. Each skill is a page of instructions that Claude follows in your Java project, using your own build and Claude Code's normal permission prompts.

Install it from this repository's marketplace:

```
/plugin marketplace add narrativetrace/narrativetrace-java
/plugin install narrativetrace-java@narrativetrace-java
```

## Skills

- `narrativetrace-doctor`: Diagnoses a NarrativeTrace Java install and configuration.
- `add-narrative-tracing`: Installs NarrativeTrace into a Java project and gets it to a first trace.
- `add-narrativetrace-clarity`: Adds or verifies NarrativeTrace Clarity in a Java Gradle project.
- `narrativetrace-feedback`: Reports a defect in NarrativeTrace itself — the library, the doctor, an agent skill, or the published install prompt.
- `narrativetrace-verify`: Verifies a change in a Java project by reading what the code actually did before saying it is done.
- `narrativetrace-debug`: Finds the cause of a wrong result in a Java project by reading what the code did with the values, not by stepping through it.
- `narrativetrace-explain`: Use when someone asks what an application or a flow actually does, with nothing broken and no change needing verification.

## What the skills run

The plugin pre-approves no command. The first time a skill runs one of these in your project, Claude Code asks you.

- `./gradlew` with the tasks `approveNarratives`, `build`, `clarityCheck`, `clarityScan`, `clean`, `narrativetraceDoctor`, `narrativetraceFeedback`, `narrativetraceInit`, `run`, `test`. `build`, `run` and `test` run your project's own build, application and tests. The NarrativeTrace tasks read your project and write under `build/narrativetrace/` and `src/test/narratives/`; `narrativetraceInit --diff` only previews an install and writes nothing. Applying the install, which writes `AGENTS.md` and skill files into your project, and promoting an approval baseline with `approveNarratives` are separate steps that wait for your yes.
- `git` and `find`, to read what is in your project:

```
find build/narrativetrace -name "*.md"
git status --short src/test/narratives
find build/narrativetrace -name "clarity-scan-results.json"
find build/narrativetrace -name "clarity-scan-report.md"
find build/narrativetrace/feedback -name "*.md"
find build/narrativetrace/structural -name "*.nt"
find build/narrativetrace/traces -name "*.md"
find src/test/narratives -name "*.received.nt"
find build/narrativetrace/diagrams -name "*.mmd"
```

Two skills also edit your project's files: they add the NarrativeTrace dependencies or Gradle plugin to your build file and, in a project with no entry point, a small console app that shows a first trace.

## What the skills fetch

- The newest release number of NarrativeTrace, with one read-only request to Maven Central's metadata for the core library, so the skills never write a version from memory. The skills tell Claude to run this, or to open the same address with any fetch tool:

```
curl -s https://repo1.maven.org/maven2/ai/narrativetrace/narrativetrace-core/maven-metadata.xml | grep -o '<release>[^<]*' | cut -d'>' -f2
```

- Dependencies: your own Gradle build resolves the NarrativeTrace libraries and Gradle plugin, and what they depend on, from Maven Central and the Gradle Plugin Portal, as it resolves any dependency. The skills fetch nothing else.

## What the skills send

Nothing leaves your machine from this plugin: no skill uploads, posts or transmits anything, and it has no hooks, MCP servers or network code of its own.

`narrativetrace-feedback` is the one skill about reporting a defect in NarrativeTrace itself. It drafts a report from your project into `build/narrativetrace/feedback/` (the install coordinates, the doctor's JSON report and at most one structural trace, and it refuses to write a report that carries a value from your own run), shows you the whole draft, and asks once whether to file it. Only after your yes does it print a pre-filled issue address (https://github.com/narrativetrace/narrativetrace-java/issues/new?...) and the name of the body file to paste. It never opens that address and never runs the command it prints: nothing is sent unless you open the address yourself, in your own browser, under your own account, and the issue you file is public.

## License and links

The skills in this plugin are licensed under Apache-2.0.

- Homepage: https://narrativetrace.ai
- Repository: https://github.com/narrativetrace/narrativetrace-java

NarrativeTrace is provided as is: no warranty, and no guarantee that it fits your purpose. Use it at your own risk.

The Apache-2.0 text, [LICENSE-APACHE](https://github.com/narrativetrace/narrativetrace-java/blob/main/LICENSE-APACHE), section 7:

> 7. Disclaimer of Warranty. Unless required by applicable law or
> agreed to in writing, Licensor provides the Work (and each
> Contributor provides its Contributions) on an "AS IS" BASIS,
> WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
> implied, including, without limitation, any warranties or conditions
> of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
> PARTICULAR PURPOSE. You are solely responsible for determining the
> appropriateness of using or redistributing the Work and assume any
> risks associated with Your exercise of permissions under this License.

Section 8, "Limitation of Liability", of the same file limits liability further (Apache-licensed parts only).

The NarrativeTrace runtime that these skills install is licensed under the Business Source License 1.1; see [LICENSE](https://github.com/narrativetrace/narrativetrace-java/blob/main/LICENSE).
