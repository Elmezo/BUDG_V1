# Environment (ENV) bulk migration

This document describes how full-tenant ZIP export/import behaves today, what is safe in **Replace** mode, and known limitations. Treat this as the stabilization contract until E2E runs are green.

## Package layout

- **`metadata.json`** — `packageType` must be `budg_env`, `schemaVersion` must match `EnvironmentMigrationConstants.SCHEMA_VERSION`. Importer rejects unknown versions. When the export used stable workbook names, `excelNaming` is `stable_v1`.
- **`manifest.json`** — ordered list of `{ targetRef, fileEntry }` entries consumed by the parallel import pipeline.
- **Workbooks** — in ENV mode, facet files use fixed names (e.g. `policies.xlsx`, `datasets.xlsx`), relationships use `rel_<key>.xlsx`, roles use `roles_<facet>.xlsx` (see `DatasetMigrationService`).

## Replace vs merge

| Mode | Behavior |
|------|----------|
| **Replace** | Truncates governance tables (relationship junctions, core facet tables, stakeholder links) before import. Does **not** truncate users, authentication, audit tables, job tables, Envers `revinfo`, or `flyway_*` migration history. Implementation: `EnvironmentCoreTruncator` and `isTableProtectedFromReplaceTruncate`. |
| **Merge** | No pre-import truncate. Import uses merge-oriented options only where supported (see below). |

## Merge mode semantics (limitations)

Merge is **not** a full relational upsert for every artifact:

- **Registry relationships** (junction sheets): treated as **insert-only** in merge — `shouldUseMergeUpsert` returns false for relationship `targetRef`s (`ParallelImportProcessor`).
- **Roles** and **hierarchy**-named entities: **insert-oriented** in merge (no merge upsert path).
- **Ordinary facet entities** (policies, datasets, etc.): merge may use **Update Existing Items** / processor **UPDATE** where the Python path supports it.

Until each processor guarantees idempotent upsert by business key, assume merge can create duplicates or skip updates for some entity types.

## End-to-end validation checklist (stabilization)

Run on a **non-production** database clone.

1. **Export** — full-tenant / ENV package from source environment; confirm ZIP contains `metadata.json`, `manifest.json`, and expected stable `.xlsx` names.
2. **Import (Replace)** — target empty governance on a **clean** environment (or dedicated schema): upload ZIP, Replace mode, wait for job success.
3. **Verify** — spot-check counts and samples for facets, relationship junctions, and role assignments; confirm `people` row counts unchanged; confirm audit/auth/job tables still populated if they existed pre-import.
4. **Import (Merge)** — optional second pass on a copy with partial data to observe insert-only junction behavior and document any duplicates.

## Data quality (follow-up work)

Failures after wiring fixes are often content issues. Stabilization backlog:

- **Dates** — normalize Excel serial dates and locale strings before validation.
- **Enums** — align sheet values to canonical enum labels (typos cause hard failures).
- **Reference data** — ensure taxonomy/glossary/regulatory rows exist before importing dependents; treat missing FK targets as validation errors with clear row context.

Do not expand product scope until the checklist above is repeatable on representative packages.
