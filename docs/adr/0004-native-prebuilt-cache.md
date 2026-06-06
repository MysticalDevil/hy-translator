# ADR 0004: Native Prebuilt Cache

## Status

Accepted.

## Context

The `:lib` module bridges Kotlin to llama.cpp through JNI. Rebuilding
llama.cpp for each Android ABI is expensive, but relying on implicit
developer-machine state makes the project hard to reproduce.

## Decision

Support a local prebuilt cache under:

```text
lib/src/main/prebuilt/<abi>/
```

The native build can link existing complete artifacts from that cache. If
the expected libraries are missing, the build path can compile llama.cpp
instead of silently succeeding with incomplete native output.

The cache is a build optimization, not an application architecture
boundary. App code talks to app/domain abstractions, not to prebuilt file
locations.

## Consequences

Local iteration is faster when native outputs are already present.
Clean-machine builds remain diagnosable because missing artifacts trigger
the native build path.

The cache should stay out of app business logic and should not hide
broken ABI coverage. Verification should inspect required ABI artifacts
when native build behavior changes.
