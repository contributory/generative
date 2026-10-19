# Plan: Configurable Image/Video Generation API Mapping

**Status:** design only; no implementation starts until this plan is approved.

## Goal

Remove all Agnes-specific behavior from the default image/video tools. Replace it with one provider-agnostic request/response mapper in:

`Default & Tools → Image/Video Generation`

The configured provider URL, authentication, HTTP method, and headers remain the provider/API connection responsibility. This feature controls the media request body and response interpretation.

The embedded editor describes the fields once; a mode switch controls whether that mapping is serialized as a multipart form or a JSON body.

## User-facing configuration

```text
Default & Tools
└── Image/Video Generation
    ├── Request
    │   ├── Mode: Form | JSON
    │   ├── File transport: file | base64
    │   └── JSON/form editor
    └── Response
        ├── Type: JSON | image | video | octet-stream
        └── JSON media key/path + value kind: link | base64
```

## Request variables

| Variable | Type | Meaning |
| --- | --- | --- |
| `{{prompt}}` | string | Text entered by the user or supplied by the AI tool call. |
| `{{input_files}}` | list | Every file supplied by the user or AI. Each item resolves as a file or base64 according to the selected transport. |
| `{{input_file[0]}}` | one file value | The first supplied file. The same syntax supports `[1]`, `[2]`, and so on. |

Example JSON body:

```json
{
  "prompt": "{{prompt}}",
  "references_images": "{{input_files}}"
}
```

The editor must validate the template as JSON and preserve typed values where possible: a list variable remains a JSON list when used as a value, while quoting it explicitly produces a string.

## Response mapping

```text
API response
├── JSON
│   └── media path: data.image.content
│       value kind: link | base64
├── image
├── video
└── octet-stream
```

For JSON responses, the user selects the path that contains the generated media. The path supports object keys, numeric indexes, and a list wildcard:

```text
data.image.content      -> one image/video
data.images[0].content  -> first item
data.images[].content   -> all items
```

`link` downloads the URL; `base64` decodes the value. `image`, `video`, and `octet-stream` responses are read directly from the HTTP body and stored as generated media using the response content type.

## Runtime flow

```text
User / AI tool call
        │ prompt + input files
        ▼
Image/Video tool
        │ resolve {{prompt}}, {{input_files}}, {{input_file[n]}}
        ▼
Request mapper ── Form or JSON ── file or base64
        │
        ▼
Configured provider API
        │
        ▼
Response mapper ── JSON path / link / base64 / raw bytes
        ▼
Local generated media ──► chat result
```

## Implementation slices

1. Delete Agnes detection, Agnes-only request builders, validations, URL rules, and response handling.
2. Add persisted image/video mapping models with safe defaults and migration support.
3. Add the Request and Response editors to the existing Default & Tools screen.
4. Implement typed variable substitution, form/JSON request construction, file/base64 conversion, and validation errors.
5. Implement JSON-path extraction and direct image/video/octet-stream decoding.
6. Add tests for indexed/list files, both transports, all response forms, wildcard paths, invalid templates, and missing media values.

## Acceptance criteria

- No image/video execution path depends on the provider being named Agnes.
- A user can send a prompt and zero or more input files through either Form or JSON.
- A user can choose file or base64 for each mapped file value, including list and indexed variables.
- A user can extract one or many media values from JSON using the paths above, or receive raw media bytes directly.
- Invalid request JSON, unresolved variables, invalid response paths, and unsupported media values produce clear errors instead of silently losing output.

## One assumption to confirm

This plan keeps endpoint, authentication, HTTP method, and headers in the existing provider configuration. If those should also be editable inside Image/Video Generation, that should be added before implementation.
