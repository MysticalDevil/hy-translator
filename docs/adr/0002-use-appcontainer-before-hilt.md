# ADR 0002: Use AppContainer Before Hilt

## Status

Accepted as an interim step.

## Context

The target architecture uses standard Android dependency injection, with
Hilt as the likely final implementation. The codebase previously created
repositories, controllers, and notifiers directly from UI entry points.
Jumping straight to Hilt while other boundaries were still moving would
combine dependency injection migration with behavioral refactors.

## Decision

Introduce `HyTranslatorApplication` and `DefaultAppContainer` first.
`MainActivity` and `TranslatorRoute` no longer construct repositories or
download controllers directly. The container centralizes construction of:

- repositories;
- download action controllers;
- notification adapters;
- ViewModel factory;
- Route-scoped OCR adapter factories.

## Consequences

The app now has one clear object graph entry point, which makes the
future Hilt migration mechanical. Teaching material can show the same
dependency direction before and after Hilt.

This is not the final DI solution. Service injection is still manual, and
`DefaultAppContainer` should eventually be replaced by Hilt modules,
`@HiltAndroidApp`, and `@HiltViewModel`.
