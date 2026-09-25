# ADR-003 — Separate snapshot operations from live collaboration

**Status:** Proposed — complete during Lab #6.

## Context
The application already has a REST API and now needs multiple browsers to observe changes in the same Board without polling.

## Decision to complete
Document why the team keeps REST for bootstrap/snapshot operations while using STOMP/WebSocket for collaboration events.

At minimum compare:

- request/response vs asynchronous notification;
- resource snapshot vs interaction event;
- coupling of client behavior;
- failure/reconnection behavior;
- effect on the Board contract.

## Consequences
Record at least two benefits, two costs and one limitation accepted for the course scope.
