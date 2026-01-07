# Transaction Upload request_nmbr Fix

## Issue
The `request_number` field was not being stored with the format `request_nmbr-<file_id>` when uploading transaction files via `/recon-service/api/v1/reconciliation/transaction-uploads`.

## Root Cause
The column length was set to VARCHAR(40) which could cause truncation when appending the upload_id suffix to longer request numbers.

## Changes Made

### 1. Database Migration
**File:** `db-migration/src/main/resources/db/migration/reconciliation/V15__increase_request_nmbr_length.sql`
- Increased `request_nmbr` column from VARCHAR(40) to VARCHAR(100)
- Added comment explaining the format

### 2. Entity Update
**File:** `recon-service/src/main/java/com/example/paymentreconciliation/entity/TransactionSearchDetail.java`
- Updated JPA annotation from `length = 40` to `length = 100`

### 3. Enhanced Logging
**File:** `recon-service/src/main/java/com/example/paymentreconciliation/service/TransactionUploadService.java`
- Added debug logging in `appendUploadScopedRequestNmbr()` method to show the transformation
- Added debug logging in CSV processing loop to track original vs stored values

## How It Works

The code flow is:
1. CSV is uploaded with columns: `txn_ref`, `wage_list` (or `request_nmbr`), `txn_date`, `txn_amount`
2. For each row, `extractRequestNmbr()` reads the `request_nmbr` or `wage_list` column
3. `appendUploadScopedRequestNmbr()` appends "-<upload_id>" to the value
4. The scoped value is stored in the database

Example transformation:
- CSV value: `WL-20260107-00000104`
- Upload ID: `123`
- Stored value: `WL-20260107-00000104-123`

## Testing Instructions

### 1. Apply Database Migration
```bash
cd db-migration
mvn flyway:migrate -Dflyway.configFiles=flyway.conf
```

Or restart the db-migration service if using Docker/auto-migration.

### 2. Rebuild recon-service
```bash
cd recon-service
mvn clean package
```

### 3. Test Upload
```bash
# Upload a transaction file
curl -X POST http://localhost:8084/recon-service/api/v1/reconciliation/transaction-uploads \
  -H "Authorization: Bearer <your_jwt_token>" \
  -F "file=@src/main/resources/sample/transaction_upload_sample_copyv2.csv" \
  -F "uploadedBy=test_user"
```

### 4. Verify in Database
```sql
-- Check the stored values
SELECT 
    id,
    upload_id,
    line_no,
    txn_ref,
    request_nmbr,
    txn_date,
    txn_amount,
    status
FROM reconciliation.transaction_search_details
WHERE upload_id = <upload_id_from_response>
ORDER BY line_no;

-- Expected: request_nmbr should be 'WL-20260107-00000104-<upload_id>'
```

### 5. Check Logs
Enable debug logging by adding to `application-dev.yml`:
```yaml
logging:
  level:
    com.example.paymentreconciliation.service.TransactionUploadService: DEBUG
```

Look for log entries like:
```
DEBUG ... Processing CSV line 2: originalRequestNmbr='WL-20260107-00000104', storedRequestNmbr='WL-20260107-00000104-123'
DEBUG ... Appending upload scope: 'WL-20260107-00000104' + uploadId 123 = 'WL-20260107-00000104-123'
```

## Verification Checklist
- [ ] Migration V15 applied successfully
- [ ] recon-service rebuilt and restarted
- [ ] Test upload completed successfully
- [ ] Database shows request_nmbr with format: `<original_value>-<upload_id>`
- [ ] No truncation errors in logs
- [ ] Column length verified: `SELECT character_maximum_length FROM information_schema.columns WHERE table_name='transaction_search_details' AND column_name='request_nmbr';` (should return 100)

## Rollback (if needed)
```sql
ALTER TABLE reconciliation.transaction_search_details
ALTER COLUMN request_nmbr TYPE VARCHAR(40);
```

Note: Only rollback if no data exceeds 40 characters.
