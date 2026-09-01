# Plan 035 — App Store Submission (Phase 1C)

> Status: TODO
> Depends on: 034 (feature-complete build)
> Effort: S–M (~2 weeks including review cycles)

## Why

Shipping. App Review is an external process with its own clock; this phase is
preparation + response, not development.

## Scope

1. **Assets**: app icons, screenshots (6.7" + required set), metadata, keywords,
   support URL.
2. **Compliance pack**: privacy policy page (hosted), nutrition labels matching the
   data-safety truth (account ID, device metadata, NO content collection), account
   deletion path verified working against the server, export compliance (uses
   standard crypto only — ATS/h3 self-signed exception documented).
3. **TestFlight**: internal QA round → external beta (this is also the
   push-notification, signing, and provisioning shakedown).
4. **Submission + response**: expect 1–2 rejections on first pass (local-network
   prompts, background justification are common); each response is a documented
   Q&A, never a silent behavior change.

## STOP conditions

- Never ship a build that fakes a feature for review (never-fake-success law applies
  to App Review too).
- Rejections that would require weakening security (e.g. removing the self-signed
  pinning) STOP and consult the user — security invariants outrank ship dates.
- TestFlight crashes block submission — fix, never "works on my machine".

## Verification

App Store Connect status: Approved & released. CHANGELOG entry. Plan flipped DONE with
the marketing/download links recorded.
