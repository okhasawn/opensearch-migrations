# Deploy Jenkins to ECS using AWS CDK

## Deploying

Install npm packages:

`npm install`

Set the following env variablest:
* `CDK_DEFAULT_ACCOUNT=<your-aws-account-id>`
* `CDK_DEFAULT_REGION=<aws-region>`

Then run this command:

`cdk deploy --context hostedZoneName=<hosted-zone-name>`

