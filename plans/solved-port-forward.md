# Port Forwarding Plan

Goal: add a **Port Forwarding** group to the machine editor (`MachineDetailsScreen`), placed **between "SFTP files" and "SSH options"**. It has one on/off switch per forward type — **Local**, **Remote**, **Dynamic** — and, for each type, the ports to forward.

This file is the progress log: if a session is interrupted, resume at the first unchecked item. Work one item at a time, easy → hard. Do not build, commit or push unless asked; run lint + tests once at the end (see `AGENTS.md`).

## Behavior

| Type | JSch API | Rule format (one per line / comma separated) | Example |
|---|---|---|---|
| Local (`-L`) | `session.setPortForwardingL(bind, lport, host, rport)` | `8080` or `8080:10808` (explicit `8080:host:10808` still accepted) | `8080:10808` = device 8080 → machine 10808 |
| Remote (`-R`) | `session.setPortForwardingR(bind, rport, host, lport)` | `8080` or `8080:10808` (explicit `8080:host:10808` still accepted) | `8080:10808` = machine 8080 → device 10808 |
| Dynamic (`-D`) | none in JSch — own SOCKS5 server over `direct-tcpip` channels | `localPort` | `1080` |

- Each type has an independent toggle (default **off**) and its own rules field. A toggle that is off keeps its rules saved but applies nothing.
- Local/Dynamic listeners bind to `127.0.0.1` only. Remote binds on the SSH server's loopback (`GatewayPorts` is a server-side setting).
- Ports are 1–65535. Local/Dynamic ports must be unique within the machine; port `0` is not accepted (the user must say which port).
- Forwards are applied right after `session.connect(...)` succeeds and die with the session. A forward that fails (port in use, server forbids `AllowTcpForwarding`) must not fail the connection — it is reported as a non-fatal warning/log.
- A machine with at least one active forward is **exempt from the 5-minute idle disconnect** (`IDLE_DISCONNECT_MILLIS`); otherwise the forwards would silently disappear. Explicit disconnect still closes it.
- Applies to SSH and Cloud Shell machines (Cloud Shell is also an SSH session).

## Data model

`shared/model/RemoteMachineModels.kt`: new serializable class and a field on `RemoteMachine` with defaults, so stored machines still decode:

```kotlin
@Serializable
data class PortForwardingConfig(
    val localEnabled: Boolean = false,
    val localRules: List<String> = emptyList(),   // "8080:db.internal:3306"
    val remoteEnabled: Boolean = false,
    val remoteRules: List<String> = emptyList(),  // "9090:localhost:3000"
    val dynamicEnabled: Boolean = false,
    val dynamicPorts: List<Int> = emptyList()     // 1080
)
// RemoteMachine: val portForwarding: PortForwardingConfig = PortForwardingConfig()
```

Parsing/validation helpers sit next to it (pure functions, unit-testable): `parseLocalRule`, `parseRemoteRule`, `parseDynamicPort`.

## Checklist

1. [x] **Strings**: `res/values/strings.xml` (`translatable="false"` like neighbours): section title/help, three toggle labels + descriptions, three field labels + hints, validation errors.
2. [x] **Model**: `PortForwardingConfig` + `portForwarding` field + rule parsing/validation helpers.
3. [x] **UI state**: in `MachineDetailsScreen`, `var portForwarding by remember(initial.id) { ... }`, included in `save()` via `initial.copy(portForwarding = ...)`.
4. [x] **UI group**: `RemoteSection(R.string.remote_machine_port_forwarding_section)` inserted between the SFTP and SSH options sections; per type a `RemoteToggle` and, when enabled, a `RemoteTextField` (multi-line for Local/Remote, digits for Dynamic). If `MachineDetailsScreen.kt` grows too large, put it in `PortForwardingSection.kt` in the same `details/ui` folder.
5. [x] **Validation on save**: `RemoteMachineService.saveMachine` normalizes/trims and rejects invalid or duplicate ports with a clear message (shown by the existing `error` text).
6. [x] **Apply Local + Remote**: in `RemoteMachineService.connect`, after `session.connect`, loop over enabled rules and call `setPortForwardingL/R`, catching `JSchException` per rule.
7. [x] **Idle exemption**: `RemoteMachineConnectionManager` skips `disconnectIfIdle` when the machine has an enabled forward.
8. [x] **Dynamic (SOCKS5)**: `integrations/remote/RemoteSocksForwarder.kt`: local `ServerSocket` on `127.0.0.1:port`, SOCKS5 no-auth CONNECT (IPv4, IPv6, domain); each client opens a `direct-tcpip` channel and pumps both directions on `Dispatchers.IO`; closed when the session disconnects.
9. [x] **Docs**: add new files to the tree in `docs/structure.md`.
10. [x] **Tests**: unit tests for rule parsing/validation (`RemoteMachineTest.kt`); then run lint + tests once.

## Non-goals

No per-rule toggles, no custom bind addresses, no SOCKS authentication, no UDP, no changes to other screens.

## Status

All items done. `testDebugUnitTest`: `RemoteMachineTest` passes (14/14, incl. the 3 new). Failing unrelated tests: `AccountAdapterTest`, `AccountModelDiscoveryTest`, `AccountTokenUsageTest`, `ChatOpeningScrollTest`. `lintDebug` has not been run because Gradle stopped at those failures. Forward changes made in the editor apply on the machine's next connection.


## Update: simpler rules

Rules are `port` (same port both sides) or `listen:target` (different ports, `->` also accepted); host defaults to `localhost`. The first number is always the port that is opened (device for Local, machine for Remote).
