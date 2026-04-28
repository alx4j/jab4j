# Codec Fixture Corpus

This directory freezes the initial codec-focused conformance corpus required by `TASK-009`.

- Positive fixtures store committed logical tiles, diagnostics, and payload bytes for the supported subset.
- Negative fixtures reference a positive base fixture plus a deterministic mutation or alternate decode profile.
- `catalog.txt` is the canonical test order.
- `CORPUS_METADATA.yaml` records codec-profile provenance for the corpus as a whole.

Naming rules:

- `positive/codec-roundtrip-*.properties` for supported round-trip vectors
- `negative/codec-*.properties` for negative decode and profile cases
