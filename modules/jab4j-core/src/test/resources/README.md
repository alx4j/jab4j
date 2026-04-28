# Conformance Fixture Storage

This path is reserved for generated offline conformance fixtures, golden vectors, and related codec metadata.

`fixture-prerequisites/` contains the fixture metadata template and generation notes.

The first codec-focused corpus now lives under `codec-fixtures/` with:

- frozen positive logical-tile vectors for the milestone-one supported subset,
- negative fixtures for unsupported profiles and corrupted codec inputs,
- and codec-profile provenance metadata for the corpus and each fixture file.
