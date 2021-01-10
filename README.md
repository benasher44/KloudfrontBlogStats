# Kloudfront Blog Stats

A lambda function and CLI tool for processing AWS Cloudfront access logs in Kotlin and dumping the useful data into postgres.

## Why?

My blog ([benasher.co](https://benasher.co)) is a static site hosted via S3 and AWS Cloudfront. I built this tool, so that I could get longer-living page view data beyond the 60-day retention period that Cloudfront gives you in its reports.

## Goals

Be able to write queries to assess:

1. Views per day, week, month for the site and per page
1. Top referers
1. Sanitize AWS log data to prepare it to be queried
1. Run serverless to keep costs low— primary use case is occassional usage (site owner occasionally runs queries)

Cloudfront gives you reports for some of this information, but the data only goes back 60 days. Processing logs into a database allows quer

## Non-Goals

1. Store data that would allow tracking users or locations

## What it does

This parses Cloudfront access logs and extracts:

1. Access date and time in UTC
1. Referer header
1. User Agent
1. Path component of the URL accessed

🚨 All paths are normalized to remove the trailing slash. Once a log is processed, the extracted data is dumped into postgres, and the log file is *deleted from S3* 🧹.

## Usage

### Important Environment Variables

* [SDK credentials](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html)
* `PG_USER`: The postgres database user
* `PG_PASSWORD`: The postgres database password
* `PG_URL`: The postgres database url in the format: `postgresql://YOUR_DB_LOCATION/YOUR_DB_NAME`
* `LOG_BUCKET_REGION` (unless supplied on the command line): The AWS region where your S3 bucket lives
* `LOG_BUCKET` (unless supplied on the command line): The name of the bucket where the logs live, to be parsed.

### Deploy to Lambda

The below assumes you have the aws cli tool setup, and AWS credentials configured for it.

1. `./gradlew clean fatJar`
1. Command to create the function (pay attention to all caps variables that need substitution):
```
aws lambda create-function --function-name YOUR_FUNCTION_NAME --runtime java8 \
    --zip-file fileb://build/libs/KloudfrontBlogStats-1.0-SNAPSHOT-fat.jar --handler com.benasher44.kloudfrontblogstats.AppKt::s3Handler \
    --role YOUR_ROLE_FOR_LAMBDA \
    --vpc-config YOUR_VPC_CONFIG \
    --environment "Variables={LOG_BUCKET=YOUR_LOG_BUCKET,LOG_BUCKET_REGION=YOUR_S3_BUCKET_REGION,PG_URL=postgresql://YOUR_DB_LOCATION/YOUR_DB_NAME,PG_USER=YOUR_PG_USER,PG_PASSWORD=YOUR_PG_PASSWORD}" \
    --timeout 300 \
    --memory-size 512
```

### CLI

1. `./gradlew clean fatJar`
1. `java -jar build/libs/KloudfrontBlogStats-1.0-SNAPSHOT-fat.jar --help`

This is mainly useful for testing, though you could run it locally and not pay for AWS Lambda at all. By default, the CLI tool does not delete logs from S3. See the help text for how to enable that.

## Useful Resources

* [Configuring and using standard logs (access logs)](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/AccessLogs.html)
* [Kotlin and Groovy JVM Languages with AWS Lambda](https://aws.amazon.com/blogs/compute/kotlin-and-groovy-jvm-languages-with-aws-lambda/)
* [Tutorial: Using AWS Lambda with Amazon S3](https://docs.aws.amazon.com/lambda/latest/dg/with-s3-example.html)
* [Tutorial: Configuring a Lambda function to access Amazon RDS in an Amazon VPC](https://docs.aws.amazon.com/lambda/latest/dg/services-rds-tutorial.html)
* [Why can’t I connect to an S3 bucket using a gateway VPC endpoint?](https://aws.amazon.com/premiumsupport/knowledge-center/connect-s3-vpc-endpoint/)
