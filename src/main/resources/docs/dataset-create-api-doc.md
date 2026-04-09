# Dataset Create API

- Endpoint: `/api/dataset/create`
- Method: `POST`
- Content-Type: `application/json`

## Request Body
```json
{
  "primaryName": "Customer Data",
  "masterSource": 1,
  "refNumber": "DS006",
  "definition": "Customer information dataset",
  "glossary": 3,
  "usage": "Business reporting",
  "status": 1,
  "accessControlType": 2,
  "lifecycle": 1
}
```

## Validation
- `primaryName` (required)
- `masterSource` (required)
- `definition` (required)
- `glossary` (required)
- `status` (required)
- `accessControlType` (required)
- `lifecycle` (required)
- `refNumber`, `usage` optional

## Success Response
```json
{
  "status": "success",
  "message": "Dataset created successfully",
  "datasetId": 6
}
```

## Error Response
```json
{
  "status": "error",
  "message": "Primary Name is required."
}
```

## Notes
- Uses JDBC via existing `DatabaseConnection`.
- Returns 400 for validation/constraint errors; 500 for unexpected errors.
