# Plan: Configurable Image/Video Generation API Mapping

**Status:** implementation started; the request-chain design below is the implementation contract.

## Goal

Remove all Agnes-specific behavior from the default image/video tools. Preserve the existing standard provider integrations as the Default mode, and add a provider-agnostic request/response mapper as the Custom mode in:

`Default & Tools → Image/Video Generation`

The configured provider base URL and secret source remain the provider/API connection responsibility. Each request step adds its own endpoint path (for example, `/video/create`), request method, and editable headers.

The Default mode continues to use the existing image/video settings and standard provider API implementations. Parameter-visibility switches belong in that Image/Video settings screen, not in the separate Tools parameter list. The Custom mode uses reusable profiles with an embedded JSON Editor; a mode switch controls whether the active profile mapping is serialized as a multipart form or a JSON body.

## User-facing configuration

```text
Default & Tools
└── Image/Video Generation
    ├── Mode: Default | Custom
    │   ├── Default: standard API + parameter visibility in Image/Video settings
    │   └── Custom: select or edit a reusable profile
    ├── Request chain profile editor (Custom mode only)
    │   ├── Step 1: endpoint + method + headers + JSON Editor
    │   ├── Step 2..N: endpoint + method + headers + JSON Editor
    │   ├── Mode per step: Form | JSON
    │   ├── File transport: file | base64
    │   └── Response observation + retry rules per step
    └── Final response
        ├── Type: JSON | image | video | octet-stream
        └── If Type = JSON: user-defined media key/path + value kind: link | base64
```

## Request variables

| Variable | Type | Meaning |
| --- | --- | --- |
| `{{prompt}}` | string | Text entered by the user or supplied by the AI tool call. |
| `{{model}}` | string | The model ID selected for the image/video generation request. |
| `{{api_token}}` | string | A runtime environment variable containing the configured API token, available for header or body mappings. It is resolved at request time and must not be written to logs or exported mapping text. |
| `{{input_files}}` | list | Every file supplied by the user or AI. Each item resolves as a file or base64 according to the selected transport. |
| `{{input_file[0]}}` | one file value | The first supplied file. The same syntax supports `[1]`, `[2]`, and so on. |

These variables are optional. The JSON Editor must not require any of them, inject them into the request mapping, or reject a mapping because one is absent. All of these are valid mapping choices:

```json
{
  "prompt": "{{prompt}}"
}
```

```json
{
  "references_images": "{{input_files}}"
}
```

```json
{}
```

The editor only validates the mapping syntax and supported variable syntax. It must preserve typed values where possible: a list variable remains a JSON list when used as a value, while quoting it explicitly produces a string. Input files are sent only when the mapping references them.

Example mapping for an API that accepts a model, prompt, size, ratio, and nested response format:

```json
{
  "model": "{{model}}",
  "prompt": "{{prompt}}",
  "size": "2K",
  "ratio": "16:9",
  "extra_body": {
    "response_format": "url"
  }
}
```

The fields and values in this example are user-defined mapping content; only the `{{...}}` placeholders are resolved by the tool.

## Multi-request workflow

Some APIs return a task/job from the create request and require a second request to retrieve the generated media. The user can add an ordered chain of request steps. Every step has its own endpoint, method, headers, request mode, and JSON Editor.

```text
Step 1: POST /video/create
  request body -> { "model": "{{model}}", "prompt": "{{prompt}}" }
  response    -> { "data": { "id": "task-123" } }
                         │
                          └── available to later steps as {{step1.response.body.data.id}}

Step 2: GET /video/result/{{step1.response.body.data.id}}
  request body -> optional; may be {}
  response    -> { "status": "completed", "data": { "video": "https://..." } }
                         │
                         ├── completed -> extract configured media
                         ├── pending   -> wait, then retry within the configured limit
                         └── error     -> stop and show the API error
```

Each step can access:

- The global variables `{{prompt}}`, `{{model}}`, `{{api_token}}`, `{{input_files}}`, and `{{input_file[n]}}`.
- The complete JSON response body from an earlier step through paths such as `{{step1.response.body.data.id}}` or `{{step2.response.body.status}}`.
- Response headers from an earlier step through variables such as `{{step1.response.headers.location}}`.

### Headers and API token

Each request step has a headers editor. When an API token is configured, the runtime adds this default header unless the step explicitly overrides it:

```http
Authorization: Bearer {{api_token}}
```

An explicit `Authorization` header in the step's headers editor takes precedence, so APIs using a different authentication scheme or token format can override the default. Other configured headers are sent exactly as mapped. The token placeholder can also be used in other headers or in the JSON Editor, but the resolved secret is never displayed in previews, activity events, logs, or exported configuration.

The response observer for a step is configurable. It defines the path/value to inspect and rules that classify the response as **ready**, **pending**, or **error**. Pending responses may be retried only for the configured maximum attempts and/or time window, with a configured delay between attempts. If no rule matches, or the retry budget is exhausted, the workflow stops and returns the API response as an error instead of retrying indefinitely. A completed response continues to the next step or, for the final step, to media extraction.

## Response mapping

```text
API response
├── JSON
│   └── user-defined media path (example: data.image.content)
│       value kind: link | base64
├── image
├── video
└── octet-stream
```

For the final JSON response, the user selects the path that contains the generated media. There are no hardcoded response keys; the following payload and paths are examples only:

```json
{
  "data": {
    "image": {
      "content": "sfdbsdjfsdfbjsd"
    }
  }
}
```

The configurable path supports object keys, numeric indexes, and a list wildcard:

```text
data.image.content      -> one image/video
data.images[0].content  -> first item
data.images[].content   -> all items
```

`link` downloads the URL; `base64` decodes the value. `image`, `video`, and `octet-stream` responses are read directly from the HTTP body and stored as generated media using the response content type. Intermediate JSON responses are retained for later-step variables and are not treated as final media unless configured as the final response.

## Runtime flow

```text
User / AI tool call
        │ prompt + model ID + input files
        ▼
Image/Video tool
        │ resolve global variables, headers, and {{stepN.response.path}}
        ▼
Request chain
        │
        ├── Step 1: endpoint + headers + mapped request
        │       └── response stored as step1.response.body and step1.response.headers
        ├── observe path existence or pending values
        │       └── pending -> wait + retry within configured rules
        ├── Step 2..N: endpoint + headers + mapped request using prior responses
        │       └── response stored as stepN.response.body and stepN.response.headers
        └── final response mapper
                └── JSON path / link / base64 / raw bytes
        ▼
Local generated media ──► chat result
```

## Implementation slices

1. Delete Agnes detection, Agnes-only request builders, validations, URL rules, and response handling.
2. Preserve the standard Default provider execution path, move Default parameter-visibility switches into Image/Video settings, and add an explicit persisted Default/Custom mode.
3. Add persisted reusable Custom profiles (not keyed by model), with per-step header, response-observer, retry, and final media-mapping models with safe defaults and migration support.
4. Add a compact mode-aware overview to Default & Tools and edit each request/response/retry configuration in dialogs or secondary screens to avoid overflow.
5. Implement typed variable substitution for global variables (including `{{api_token}}`), `{{stepN.response.path}}`, and `{{stepN.headers.name}}`, default/overridden authorization headers, form/JSON request construction, endpoint resolution, file/base64 conversion, and validation errors.
6. Implement response observation, ready/pending/error classification, bounded delay/attempt/time-window retries, and clear API error propagation.
7. Implement final JSON-path extraction and direct image/video/octet-stream decoding.
8. Add tests for default/custom routing, indexed/list files, both transports, multi-step response references, status classification, retry limits, all response forms, wildcard paths, invalid templates, and missing media values.

## Acceptance criteria

- No image/video execution path depends on the provider being named Agnes.
- Default mode continues to use the existing standard provider API path; Custom mode is the only mode that runs the request-chain executor.
- Custom request chains are saved as reusable named profiles and remain available when the selected model changes.
- A user can send a prompt and zero or more input files through either Form or JSON.
- A user can choose file or base64 for each mapped file value, including list and indexed variables.
- A user can configure the endpoint and method for every request step, including a create request and a later result request.
- Every step can access the global variables, including `{{api_token}}`, and JSON paths from earlier responses.
- A user can edit headers for every request step; a configured token defaults to `Authorization: Bearer {{api_token}}`, while an explicit authorization header overrides it.
- A user can classify intermediate responses as ready, pending, or error and retry pending responses only within configured bounds.
- A user can extract one or many media values from the final JSON response using a user-defined path, or receive raw media bytes directly.
- Invalid request JSON, invalid endpoint paths, unresolved variables, invalid response paths, unmatched status rules, exhausted retries, and unsupported media values produce clear errors instead of silently losing output.

## One assumption to confirm

This plan keeps the provider base URL and secret source in the existing provider configuration. Endpoint path, HTTP method, and headers are editable per request step inside Image/Video Generation. The default bearer header is generated at runtime and can be overridden per step.
