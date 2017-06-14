## Fake Data Loader with Pivotal Cloud Cache (PCC)

gfsh>create region --name=transactions --type=PARTITION

dataflow:>stream create test --definition "pcc --pcc.cache.region-name=transactions | pdx-json | aws-s3 --s3.count=100 --s3.access-key=xxx --s3.secret-key=xxx --s3.bucket-name=cf-code-share --s3.bucket-region=us-east-1" --deploy
