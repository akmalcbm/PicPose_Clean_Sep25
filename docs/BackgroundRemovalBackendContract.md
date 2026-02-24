# Background Removal Backend Contract

## Endpoint
`POST /api/remove_bg`

## Request
- Content-Type: `multipart/form-data`
- Part `image`: binary image file (`image/jpeg`, `image/png`, `image/webp`)
- Part `mode`: `quality` | `offline`
- Part `size`: `preview` | `full`
- Part `format`: currently `png`

## Limits and Validation
- Max upload size: `8MB`
- Reject non-image content types
- Enforce authentication (if app user auth is required)
- Apply per-user and per-device rate limits

## Success Response
- Status: `200 OK`
- Content-Type: `image/png`
- Body: PNG bytes with alpha channel

## Error Response
- Status: `4xx` or `5xx`
- Content-Type: `application/json`

```json
{
  "error": {
    "code": "REM_BG_FAILED",
    "message": "Unable to remove background"
  }
}
```

## Timeout Guidance
- API should return within `45s` for preview requests
- For larger files, prefer `202 Accepted` + polling URL in future revisions
