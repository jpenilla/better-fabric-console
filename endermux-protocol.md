# Endermux Protocol Specification

Status: Active  
Protocol version: `10`

This document defines the wire protocol implemented by `endermux-client` and `endermux-server`.

## 1. Conformance Language

The key words `MUST`, `MUST NOT`, `SHOULD`, `SHOULD NOT`, and `MAY` are to be interpreted as described in RFC 2119.

## 2. Versioning

1. The protocol version constant is `SocketProtocolConstants.PROTOCOL_VERSION` (`10`).
2. The client MUST send its version in `HELLO.data.protocolVersion`.
3. The server MUST compare the received version to its own version.
4. If versions match, the server MUST reply `WELCOME`.
5. If versions do not match, the server MUST reply `REJECT` and close the connection.
6. Any wire-incompatible change MUST increment `PROTOCOL_VERSION`.

## 3. Transport and Framing

1. Transport is a Unix domain socket.
2. Each protocol message is one frame:
   1. 4-byte signed big-endian length prefix.
   2. UTF-8 JSON payload bytes.
3. Frame length MUST be `> 0` and `<= 1048576` bytes (`1 MiB`).
4. EOF while reading the length prefix is treated as a clean close.
5. Invalid frame length is a protocol error.

## 4. Message Envelope

Every frame payload is a JSON object with this envelope:

| Field | Type | Required | Notes |
|---|---|---|---|
| `type` | string | yes | Name of `MessageType` enum value |
| `requestId` | string | conditional | Required for request/response flows |
| `data` | object | yes | Payload object for `type` |

Example:

```json
{
  "type": "PING",
  "requestId": "d7a7f8ed-bd7c-4e56-b5e8-cc2867e2bd4c",
  "data": {}
}
```

## 5. Connection Lifecycle and Handshake

1. Client connects to the socket.
2. Client sends `HELLO` with a `requestId`.
3. Server reads the first message with handshake timeout.
4. Server responds with exactly one of:
   1. `WELCOME` if protocol is accepted.
   2. `REJECT` if request is invalid or protocol is incompatible.
5. If `WELCOME` is sent, normal message exchange begins.
6. Server then sends initial `INTERACTIVITY_STATUS`.

Handshake constraints:

1. `HELLO` MUST include `requestId`.
2. `HELLO` MUST be the first message.
3. Handshake timeout is `2000ms` with `100ms` join grace.
4. Timeout or transport failure closes the connection; timeout `REJECT` delivery is not guaranteed.

## 6. Message Catalog

### 6.1 Client to Server

| Type | Requires `requestId` | Expected response |
|---|---|---|
| `HELLO` | yes | `WELCOME` or `REJECT` |
| `COMPLETION_REQUEST` | yes | `COMPLETION_RESPONSE` or `ERROR` |
| `SYNTAX_HIGHLIGHT_REQUEST` | yes | `SYNTAX_HIGHLIGHT_RESPONSE` or `ERROR` |
| `PARSE_REQUEST` | yes | `PARSE_RESPONSE` or `ERROR` |
| `COMMAND_EXECUTE` | no | none (fire-and-forget, `ERROR` possible) |
| `PING` | yes | `PONG` or `ERROR` |
| `CLIENT_READY` | no | none |

### 6.2 Server to Client

| Type | Correlated by `requestId` | Purpose |
|---|---|---|
| `WELCOME` | yes (handshake) | Accept connection and provide server info |
| `REJECT` | yes when requestId exists | Reject handshake |
| `COMPLETION_RESPONSE` | yes | Completion results |
| `SYNTAX_HIGHLIGHT_RESPONSE` | yes | Highlighted command text |
| `PARSE_RESPONSE` | yes | Parsed line metadata |
| `LOG_FORWARD` | no | Forwarded server log event |
| `PONG` | yes | Ping response |
| `ERROR` | optional | Request error or unsolicited error |
| `INTERACTIVITY_STATUS` | no | Interactivity availability updates |

## 7. Payload Schemas

`nullable` means JSON `null` is valid.

### 7.1 Client to Server payloads

| Type | Payload fields |
|---|---|
| `HELLO` | `protocolVersion: int` |
| `COMPLETION_REQUEST` | `command: string`, `cursor: int` |
| `SYNTAX_HIGHLIGHT_REQUEST` | `command: string` |
| `PARSE_REQUEST` | `command: string`, `cursor: int` |
| `COMMAND_EXECUTE` | `command: string` |
| `PING` | _(empty object)_ |
| `CLIENT_READY` | _(empty object)_ |

### 7.2 Server to Client payloads

| Type | Payload fields |
|---|---|
| `WELCOME` | `protocolVersion: int`, `logLayout: LayoutConfig` |
| `REJECT` | `reason: string`, `expectedVersion: int` |
| `COMPLETION_RESPONSE` | `candidates: CandidateInfo[]` |
| `SYNTAX_HIGHLIGHT_RESPONSE` | `command: string`, `highlighted: string` |
| `PARSE_RESPONSE` | `word: string`, `wordCursor: int`, `wordIndex: int`, `words: string[]`, `line: string`, `cursor: int` |
| `LOG_FORWARD` | `logger: string`, `level: string`, `message: string`, `componentMessageJson: string?`, `throwable: ThrowableInfo?`, `timestamp: long`, `thread: string` |
| `PONG` | _(empty object)_ |
| `ERROR` | `message: string`, `details: string?` |
| `INTERACTIVITY_STATUS` | `available: boolean` |

### 7.3 Nested payload types

`CandidateInfo`:

| Field | Type |
|---|---|
| `value` | string |
| `display` | string |
| `description` | string? |

`ThrowableInfo`:

| Field | Type |
|---|---|
| `type` | string |
| `message` | string? |
| `frames` | `StackFrame[]` |
| `cause` | `ThrowableInfo?` |
| `suppressed` | `ThrowableInfo[]` |

`StackFrame`:

| Field | Type |
|---|---|
| `className` | string |
| `methodName` | string |
| `fileName` | string? |
| `lineNumber` | int |
| `classLoaderName` | string? |
| `moduleName` | string? |
| `moduleVersion` | string? |
| `classInfo` | `StackFrameClassInfo?` |

`StackFrameClassInfo`:

| Field | Type |
|---|---|
| `exact` | boolean |
| `location` | string |
| `version` | string |

`LayoutConfig`:

| Field | Type | Notes |
|---|---|---|
| `type` | enum | `PATTERN` or `LOGGER_NAME_SELECTOR` |
| `pattern` | string? | Active pattern |
| `selector` | `SelectorConfig?` | Logger selector config |
| `flags` | `Flags` | Layout behavior flags |
| `charset` | string? | Charset name |

`SelectorConfig`:

| Field | Type |
|---|---|
| `defaultPattern` | string |
| `matches` | `Match[]` |

`Match`:

| Field | Type |
|---|---|
| `key` | string |
| `pattern` | string |

`Flags`:

| Field | Type |
|---|---|
| `alwaysWriteExceptions` | boolean |
| `disableAnsi` | boolean |
| `noConsoleNoAnsi` | boolean |

## 8. Request/Response Rules

1. For message types marked "Requires `requestId`", the sender MUST provide a non-null `requestId`.
2. A response to a request MUST echo the same `requestId`.
3. `ERROR` MAY be correlated (with `requestId`) or unsolicited (without `requestId`).
4. `COMMAND_EXECUTE` is fire-and-forget. Command output is returned through `LOG_FORWARD`, with optional `ERROR`.

## 9. Interactivity and Log Forwarding

1. Server sends `INTERACTIVITY_STATUS` after successful handshake and whenever availability changes.
2. Interactivity-gated requests are:
   1. `COMPLETION_REQUEST`
   2. `SYNTAX_HIGHLIGHT_REQUEST`
   3. `PARSE_REQUEST`
   4. `COMMAND_EXECUTE`
3. If interactivity is unavailable, server responds with `ERROR` for gated operations.
4. Client sends `CLIENT_READY` when it is ready to consume forwarded logs.
5. Server forwards `LOG_FORWARD` messages only for clients marked ready.

## 10. Error Handling and Close Semantics

1. Invalid JSON, unknown `type`, or payload decode failure is a protocol error.
2. Transport read/write failures terminate the session.
3. There is no explicit disconnect message; disconnect is socket closure.
4. `ERROR` reports application/protocol request failures but does not require immediate disconnect.

## 11. Timeouts and Limits

| Constant | Value |
|---|---|
| Handshake timeout | `2000ms` |
| Handshake timeout join grace | `100ms` |
| Completion timeout | `5000ms` |
| Syntax highlight timeout | `200ms` |
| Max frame size | `1 MiB` |

## 12. Compatibility Policy

1. Wire compatibility is guaranteed only when protocol versions match.
2. Mixed-version peers MUST fail handshake (typically with `REJECT`).
3. If behavior and this document diverge, implementation and documentation MUST be updated together.
