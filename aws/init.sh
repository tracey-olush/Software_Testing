#!/bin/sh
# Pre-creates the S3 bucket and the DynamoDB table (with both GSIs) in LocalStack.
# The application NEVER creates these: in development this script does it, in grading the
# auto-marker does the equivalent. Run by the `aws-init` one-shot service in docker-compose.yml.
set -e

EP="--endpoint-url=http://localstack:4566"
BUCKET="${MANIFEST_ARCHIVE_BUCKET:-campusshop-dispatch-manifests}"
TABLE="${SHIPMENT_TABLE:-campusshop-shipments}"

echo "Creating S3 bucket: $BUCKET"
aws $EP s3 mb "s3://$BUCKET" 2>/dev/null || echo "  bucket already exists"

echo "Creating DynamoDB table: $TABLE (with manifest-index, carrier-dispatch-index)"
aws $EP dynamodb create-table \
  --table-name "$TABLE" \
  --billing-mode PAY_PER_REQUEST \
  --attribute-definitions \
      AttributeName=shipmentRef,AttributeType=S \
      AttributeName=manifestNo,AttributeType=S \
      AttributeName=carrierId,AttributeType=S \
      AttributeName=dispatchDate,AttributeType=S \
  --key-schema AttributeName=shipmentRef,KeyType=HASH \
  --global-secondary-indexes \
      'IndexName=manifest-index,KeySchema=[{AttributeName=manifestNo,KeyType=HASH}],Projection={ProjectionType=ALL}' \
      'IndexName=carrier-dispatch-index,KeySchema=[{AttributeName=carrierId,KeyType=HASH},{AttributeName=dispatchDate,KeyType=RANGE}],Projection={ProjectionType=ALL}' \
  2>/dev/null || echo "  table already exists"

echo "AWS resources ready."
