# Developer experience

What it takes to go from an empty project to reading your first narrative,
and where each piece of the experience lives. This page describes what ships
today; planned improvements are tracked in the private backlog.

## One-line setup

Apply the Gradle plugin and write a test. The plugin adds the NarrativeTrace
artifacts, configures the JUnit Platform, and supplies the Jupiter test
engine — the documented minimal setup runs a real test green with no further
dependencies. See the installation guide for the exact snippet per build
style.

## Per-test narratives

The `narrativetrace-junit5` module writes a narrative for every test as it
runs. Artifacts land beside the build output in the canonical formats
(`.nt` narrative text, structural and canonical JSON, chapter documents) —
the same formats every NarrativeTrace runtime emits, validated by the schemas
shipped in this repository.

## Trying it without a project

`./demo.sh` launches the example applications (e-commerce and friends) and
narrates real scenarios to the console — the fastest way to see what the
output reads like before wiring anything.

## Consuming a local build

Evaluating unreleased changes, or building an integration against this repo,
uses Gradle's composite builds — and needs **both** inclusion roles: a
`pluginManagement { includeBuild(...) }` so the plugin resolves, and a
top-level `includeBuild(...)` so the library coordinates the plugin adds
substitute to your local checkout. The plugin documentation carries the
complete `settings.gradle.kts` recipe.

## Safety while you develop

Redaction is part of the development experience, not an afterthought: a
`@NotTraced` component never appears in any rendered output — not through a
wrapper's `toString()`, not through a collection, not through an enclosing
class's own hand-written `toString()`, and not through a narration template
that names its path. If a narrative needs a value, the deliberate,
reviewable act is removing the annotation, never working around it.
