# docs/transport-spec.md — Transport contract for the mock-simulador

> Handshake document between the **frontend team** (consumers of the transport) and the **backend team** (Jeyson / Diego, who implement the real planner in Java + Spring Boot).
>
> **Normative authority**: `equipo/le/registro-requerimientos-trazados.md` §8 Contrato planificador↔frontend (congelado v1.0.0), `contract/plan-iteration.v1.schema.json` v1.0.0 (frozen), `contract/envelope.schema.json` (executable artifact of the envelope shape).
>
> **Operational specs**: `openspec/changes/mock-simulador/specs/{mock-transport-surface,mock-scenario-engine,mock-conformance-gate}/spec.md` (rev 2, all three accepted 2026-09-23).
>
> **Status**: First cross-team draft for review by Jeyson/Diego before they sign off on the Java planner integration. The mock implementation (in this repo) conforms to this document; the real Java planner must do the same.
>
> **Convention**: the protocol carries ZERO domain-shape assumptions. Anything the frontend does not need to interpret, the backend does not publish.

---

## 1. Scope

This document describes the **transport surface** that connects the frontend (and any client of the simulation) to the planner backend. The transport layer:

- identifies runs (UUIDv4, opaque),
- orders messages within a run (`seq` strictly increasing per run),
- authorizes commands (single controller per run),
- carries payloads and run-events in envelopes,
- decides nothing about the domain.

The transport does NOT carry: a domain config shape, a parameter catalog, a parameter history, world-state snapshots, or a cadence / wall-clock pacing knob. These belong to `simulation-config` (change 3, blocking) and to the planner.

## 2. REST endpoints

All paths are nested under `/api/runs`. No version segment in the URL (`/v1` is forbidden — protocol versioning lives in the envelope).

| Verb | Path | Body | Success | Errors |
|------|------|------|---------|--------|
| POST | `/api/runs` | `{config}` (opaque; transport extracts `scenario`, `seed`, `script` keys; everything else travels untouched) | 201 `{runId, controllerToken, status:"running", transportVersion}` | 400 `malformed_payload` |
| GET | `/api/runs` | — | 200 `[{runId, status, transportVersion}]` | — |
| GET | `/api/runs/{runId}` | — | 200 `Snapshot` | 404 `run_not_found` |
| POST | `/api/runs/{runId}/commands` | `{commandId, command, ...categoryBody}` (UUIDv4 client) | 202 `{accepted:true, commandId}` | 4xx (see §6) |
| POST | `/api/runs/{runId}/claim` | — | 200 `{runId, controllerToken}` (rotates token) | 404 `run_not_found` |
| WS | `/ws` | STOMP 1.2 frames | STOMP MESSAGE frames with envelope JSON | STOMP ERROR frame for protocol violations |

`{runId}` is opaque UUIDv4 — clients SHALL NOT parse it.

## 3. Envelope structure

Every STOMP MESSAGE frame body is one envelope. Exactly two shapes:

```jsonc
// Plan envelope
{
  "transportVersion": "1",
  "runId": "<uuid>",
  "seq": 1,
  "sentAt": "2026-09-01T06:00:00.000Z",
  "kind": "plan",
  "payloadVersion": "plan-iteration-1.0.0",
  "payload": { /* IterationPlan v1.0.0 — see contract/plan-iteration.v1.schema.json */ }
}

// Run-event envelope
{
  "transportVersion": "1",
  "runId": "<uuid>",
  "seq": 2,
  "sentAt": "2026-09-01T06:00:00.000Z",
  "kind": "run-event",
  "event": { "type": "<one of 7>", /* type-specific fields */ }
}
```

`additionalProperties: false` on both shapes. The envelope carries ZERO iteration fields (`iterationId`, `simTime*`, world state) — those live in `payload`. Conversely, `payload` carries ZERO transport fields (`runId`, `seq`, versions, tokens) — those live in the envelope.

Run-event vocabulary (closed, exactly 7 values):

| `event.type` | Required fields | When |
|---|---|---|
| `parameter-changed` | `commandId`, `parameter`, `value`, `effectiveFromSimTime` | engine settles hot change-parameter (next plan uses new value) |
| `incident-applied` | `commandId`, `incident` | engine settles hot inject-incident |
| `command-failed` | `commandId`, `reason` (enum: `precondition_failed` \| `stale_target`) | application-time failure of an accepted command |
| `run-paused` | `commandId` | lifecycle: pause settled at next boundary |
| `run-resumed` | `commandId` | lifecycle: resume settled |
| `run-terminated` | `commandId` | lifecycle: terminate settled (no domain terminal) |
| `controller-changed` | `newToken` | claim rotated the token |

A new event type is a `transportVersion` change.

## 4. Lifecycle state machine

```
   create → running ──┬─→ pause settled at next boundary → paused
                      │                                       │
                      │                                       ↓
                      │                                  resume settled → running
                      │
                      ├─→ terminal payload (terminal.collapse | terminal.completed) → finished(collapse|completed)
                      │
                      └─→ terminate settled → finished(terminated)
```

`status` exactly: `running | paused | finished`. `finished.kind ∈ {collapse, completed, terminated}`.

`terminate` produces NO domain terminal in any payload — the run simply stops emitting.

The 202 → 1 settlement invariant: **every `202` SHALL produce exactly one settlement event for that `commandId`, UNLESS the run reaches a terminal state first** — in which case no settlement follows, the terminal boundary is itself the implicit settlement, and clients SHALL treat pending `commandId`s as unsettled-and-discarded.

`pause` settles at the next iteration boundary: the in-flight iteration SHALL complete and its `plan` SHALL emit with the next `seq`; the `run-paused` settlement SHALL follow immediately after that boundary plan (or be the next message if no iteration was in flight).

`change-parameter` is HOT: the engine SHALL apply it without waiting for an iteration boundary; the next planning cycle SHALL use the new value. `effectiveFromSimTime` in the settlement SHALL equal the `simTimeStart` of the first window whose computation used the new value.

`inject-incident` is HOT too: the incident world-effect SHALL be expressed exclusively in subsequent `IterationPlan` payloads (`averia` action, `unavailableUnits`, reassignments). The transport SHALL NOT carry a mini-domain.

## 5. Roles

Each run has exactly one controller: the creator holding `controllerToken`. The token:

- Is sent as header `X-Controller-Token` on every `POST /api/runs/{runId}/commands`.
- Is rotated by `POST /api/runs/{runId}/claim` (synchronous `200` + emits `controller-changed` to the stream). The OLD token SHALL yield `401 invalid_token` immediately.
- The token is demo-control — NOT an authentication mechanism.

401/403 split:

| Header state | Error |
|---|---|
| absent | **403 `not_controller`** |
| present but empty | **401 `missing_token`** |
| present but wrong (or revoked) | **401 `invalid_token`** |

Observers need no credential and receive the identical stream; observers get `403 not_controller` on any command.

## 6. Error taxonomy

Receipt-time REST errors use `{code, message, details}` with the closed codes:

| Status | Code | Trigger |
|---|---|---|
| 400 | `malformed_command` | body is not a JSON object, `commandId` missing/invalid, `command` missing, or category body malformed |
| 400 | `unknown_command` | `command` value not in `{pause, resume, terminate, change-parameter, inject-incident}` |
| 400 | `unknown_parameter` | `change-parameter.parameter` not in the parameter catalog |
| 400 | `invalid_value` | `change-parameter.value` out of range for the named parameter |
| 400 | `invalid_incident` | `inject-incident` shape wrong, `kind` ≠ `averia`, `unitId` not in fleet, or `averiaType` not in `{1,2,3}` |
| 400 | `malformed_payload` | `config` not a JSON object, or `scenario` unknown |
| 401 | `missing_token` | `X-Controller-Token` header present but empty |
| 401 | `invalid_token` | `X-Controller-Token` header wrong (or revoked after claim) |
| 403 | `not_controller` | `X-Controller-Token` header absent |
| 404 | `run_not_found` | `runId` doesn't match any run |
| 409 | `invalid_state` | command incompatible with current status (e.g. pause on a paused run, terminate on a finished run, double-pause, double-terminate) |

Application-time failures use the stream event `command-failed {commandId, reason}` with `reason ∈ {precondition_failed, stale_target}`.

Receipt-time errors emit NO stream event.

## 7. Snapshot

`GET /api/runs/{runId}` returns:

```jsonc
{
  "transportVersion": "1",
  "runId": "<uuid>",
  "status": "running" | "paused" | "finished",
  "finished": null | { "kind": "collapse" | "completed" | "terminated", "atSeq": <int> },
  "lastSeq": <int>,
  "lastPlan": null | { /* IterationPlan v1.0.0 */ },
  "config": <opaque object — echoed verbatim>,
  "sentAt": "<iso8601>"
}
```

`lastPlan` semantics:
- When `running` or `paused`: the most recent `IterationPlan` emitted with `seq ≤ lastSeq`.
- When `finished(collapse)` or `finished(completed)`: `terminal.collapse.lastStablePlan` or `terminal.completed.lastStablePlan` (the plan-embedded last stable plan).
- When `finished(terminated)`: the last emitted plan (no domain terminal exists).

`null` only before the first boundary.

The snapshot SHALL NOT include: parameter catalog, history, or pending-command list (catalog explicitly deferred to `simulation-config`, change 3 blocking; owning it would duplicate domain knowledge and drift).

## 8. STOMP 1.2 subset

- CONNECT / CONNECTED with `heart-beat: cx,cy` header (deployment setting, NOT contract value).
- SUBSCRIBE with `destination: /topic/runs/{runId}/stream` and an `id` header. Subscription grants future messages only — NO replay.
- MESSAGE carries the envelope as JSON body with `content-type: application/json`, `destination`, `subscription`, `message-id`.
- DISCONNECT / RECEIPT closes the session.
- UNSUBSCRIBE removes a subscription.
- ERROR frame for: malformed frames, unknown frame commands, `accept-version` mismatch, transport-version mismatch, or a forbidden SEND (commands are REST-only).
- NO STOMP frame escaping (`\\`, `\c`, `\n`, `\r`) — the mock implementation skips escaping because every field it writes (UUIDv4, fixed destinations, numeric seqs, JSON bodies) cannot contain those sequences. The Java backend SHOULD either replicate this simplification OR add escaping for free-form header values.

Subscribing to a finished run is accepted and silent — future deliveries on that destination will be empty until the run is recreated.

## 9. Reconnection pattern

Client reconnect after disconnect:

1. SUBSCRIBE first.
2. GET snapshot for the run.
3. Render `lastPlan`, remember `lastSeq`.
4. Discard arriving MESSAGE frames with `seq ≤ lastSeq`.
5. On gap (`seq > expected+1`): GET snapshot again and re-align.

The run SHALL NOT pause for absent listeners.

## 10. Invariants

These are mandatory; deviations are bugs:

1. `seq` is a strictly increasing integer per run; first message = `seq 1`. The server is the sole authority. All subscribers of a run receive the same messages in the same `seq` order.
2. Envelope carries transport fields ONLY. Payload carries iteration fields ONLY. No field appears in both.
3. Every `202` produces exactly one settlement event for that `commandId`, UNLESS the run reaches a terminal state first — in which case no settlement follows and the `commandId` is unsettled-and-discarded.
4. Every `4xx` produces NO stream event.
5. `speedMultiplier` and any wall-clock pacing SHALL NOT surface in any protocol field. Mock-internal pacing is `O5` (mock-only, contract-invisible).
6. `config` is opaque to the transport. The transport reads ONLY the engine-known keys (`scenario`, `seed`, `script`); everything else travels verbatim and is echoed in the snapshot's `config`.
7. `runId` is opaque UUIDv4. Paths nested under `/api/runs/`. No version segment in URL.
8. STOMP SEND is rejected with an ERROR frame. Commands flow through REST only.
9. The only `inject-incident.kind` value in v1 is `averia`. Street-closure injection is out (blockages originate in source data files, not commands).
10. The `transportVersion` field in envelopes is `"1"`. An unknown `transportVersion` triggers a typed error from the server side (`UNSUPPORTED_TRANSPORT_VERSION`).

## 11. Coordination notes for the Java backend team (Jeyson / Diego)

The following are observations from the mock implementation that may help your Java implementation:

- The `run.step()` API lives on the transport seam (NOT on the engine) — see `continue-state.md` addendum, dated 2026-10-01, for the rationale. If you need a module-level stepper in the Java planner, the equivalent is a `tick(now)` method exposed to the runner; the public surface is the same.
- For deterministic re-runs (live commands mid-playback), the mock asserts that the delivered prefix is byte-identical between the original and the re-run. If your engine is not deterministic given `(scenario, seed, script)`, the integration tests in PR 4 (deferred) will catch it.
- The settlement events carry spec-mandated fields (`commandId`, `parameter`, `value`, `effectiveFromSimTime`, `incident`, `reason`, `newToken`) — see `contract/envelope.schema.json` `event` oneOf for the exact shape. The earlier artifact (PR 1) had a stub that rejected these; the artifact was amended to match the spec.
- The `effort spent` on the mock is intentionally bounded — PR 4 (gate harness + 5 test files + this doc) is the final scope. Anything beyond that is over-investment in a transient tool.

## 12. Open questions for backend review

These are NOT yet decided. Backend team should flag disagreement:

- Heart-beat negotiation values (deployment settings, not contract).
- Whether the Java planner should expose the `run.step()` API publicly or hide it behind a runner.
- Exact timing semantics for `parameter-changed.effectiveFromSimTime` (should match `simTimeStart` of the first window that USED the new value, not just the first window AFTER).
- Replay policy: backend SHOULD support late-joiners via snapshot + discard-of-arrived-low-seq, even if it doesn't replicate the mock's precomputed-queue delivery model.

---

## Sign-off

This document is the handshake for the mock-simulador transport. The frontend team signs by accepting the PR that adds it. The backend team signs by reviewing and approving it as the implementation target for the Java planner.

| Team | Signature | Date |
|---|---|---|
| Frontend (mock side) | pending — this PR | TBD |
| Backend (Java side) | pending — Jeyson / Diego review | TBD |
| Professor (canonical authority) | (not required — contract frozen in change-1) | — |

Once both signatures are in, the transport contract is frozen for v1 and any change requires a new `transportVersion`.
