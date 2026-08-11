---
title: Build and Run
status: active
owner: eQMS
last_updated: 2026-08-11
related:
  - EQMS-001
---

# Build and Run

Canonical Gradle commands for local development in this repository.

## Prerequisites

- JDK installed and available for Gradle
- Use the project wrapper: `./gradlew`

## Build

- Build all modules:
  - `./gradlew build`

## Run application entrypoints

- CLI app:
  - `./gradlew :apps:cmd:run`
- Desktop app:
  - `./gradlew :apps:desktop:run`
- Web Wasm app (dev server):
  - `./gradlew :apps:web:wasmJsBrowserDevelopmentRun`

## Web module tasks

- Build development webpack bundle:
  - `./gradlew :apps:web:wasmJsBrowserDevelopmentWebpack`
- Build browser distribution bundle:
  - `./gradlew :apps:web:wasmJsBrowserDistribution`

## Notes

- Web dev server prints the active localhost URL when started (port may vary if occupied).
- `apps/web/src/wasmJsMain/resources/index.html` is the web entry page and loads `web.js`.
