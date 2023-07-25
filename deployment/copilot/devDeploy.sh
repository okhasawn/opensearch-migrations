#!/bin/sh

# Automation script to deploy the migration solution pipline to AWS for development use case. The current requirement
# for use is having valid AWS credentials available to the environment

# Stop script on command failures
set -e

# Allow executing this script from any dir
script_abs_path=$(readlink -f "$0")
script_dir_abs_path=$(dirname "$script_abs_path")
cd $script_dir_abs_path

SECONDS=0

# Allow --skip-bootstrap flag to avoid one-time setups
skip_bootstrap=false
if [[ $* == *--skip-bootstrap* ]]
then
  skip_bootstrap=true
fi
# Allow --skip-copilot-init flag to avoid initializing Copilot components
skip_copilot_init=false
if [[ $* == *--skip-copilot-init* ]]
then
  skip_copilot_init=true
fi
# Allow --destroy flag to clean up existing resources
destroy=false
if [[ $* == *--destroy* ]]
then
  destroy=true
fi

export CDK_DEPLOYMENT_STAGE=dev
export COPILOT_DEPLOYMENT_STAGE=dev
# Will be used for CDK and Copilot
export AWS_DEFAULT_REGION=us-east-1
export COPILOT_DEPLOYMENT_NAME=migration-copilot
# Used to overcome error: "failed to solve with frontend dockerfile.v0: failed to create LLB definition: unexpected
# status code [manifests latest]: 400 Bad Request" but may not be practical
export DOCKER_BUILDKIT=0
export COMPOSE_DOCKER_CLI_BUILD=0

if [ "$destroy" = true ] ; then
  set +e
  copilot app delete
  cd ../cdk/opensearch-service-migration
  cdk destroy "*" --c domainName="aos-domain" --c engineVersion="OS_1.3" --c  dataNodeCount=2 --c vpcEnabled=true --c availabilityZoneCount=2 --c openAccessPolicyEnabled=true --c domainRemovalPolicy="DESTROY" --c migrationAssistanceEnabled=true --c mskEnablePublicEndpoints=true --c enableDemoAdmin=true
  exit 1
fi

# === CDK Deployment ===

cd ../cdk/opensearch-service-migration
if [ "$skip_bootstrap" = false ] ; then
  cd ../../../TrafficCapture
  ./gradlew :dockerSolution:buildDockerImages
  cd ../deployment/cdk/opensearch-service-migration
  npm install
  cdk bootstrap
fi

# This command deploys the required infrastructure for the migration solution with CDK that Copilot requires.
# The options provided to `cdk deploy` here will cause a VPC, Opensearch Domain, and MSK(Kafka) resources to get created in AWS (among other resources)
# More details on the CDK used here can be found at opensearch-migrations/deployment/cdk/opensearch-service-migration/README.md
cdk deploy "*" --c domainName="aos-domain" --c engineVersion="OS_1.3" --c  dataNodeCount=2 --c vpcEnabled=true --c availabilityZoneCount=2 --c openAccessPolicyEnabled=true --c domainRemovalPolicy="DESTROY" --c migrationAssistanceEnabled=true --c mskEnablePublicEndpoints=true --c enableDemoAdmin=true -O cdkOutput.json --require-approval never

# Gather CDK output which includes export commands needed by Copilot, and make them available to the environment
found_exports=$(grep -o "export [a-zA-Z0-9_]*=[^\\;\"]*" cdkOutput.json)
eval "$(grep -o "export [a-zA-Z0-9_]*=[^\\;\"]*" cdkOutput.json)"
printf "The following exports were added from CDK:\n%s\n" "$found_exports"

# Future enhancement needed here to make our Copilot deployment able to be reran without error even if no changes are deployed
# === Copilot Deployment ===

cd ../../copilot

# Allow script to continue on error for copilot services, as copilot will error when no changes are needed
set +e

if [ "$skip_copilot_init" = false ] ; then
  # Init app
  copilot app init $COPILOT_DEPLOYMENT_NAME

  # Init env, start state does not contain existing manifest but is created on the fly here to accommodate varying numbers of public and private subnets
  copilot env init -a $COPILOT_DEPLOYMENT_NAME --name $COPILOT_DEPLOYMENT_STAGE --import-vpc-id $MIGRATION_VPC_ID --import-public-subnets $MIGRATION_PUBLIC_SUBNETS --import-private-subnets $MIGRATION_PRIVATE_SUBNETS --aws-access-key-id $AWS_ACCESS_KEY_ID --aws-secret-access-key $AWS_SECRET_ACCESS_KEY --aws-session-token $AWS_SESSION_TOKEN --region $AWS_DEFAULT_REGION
  #copilot env init -a $COPILOT_DEPLOYMENT_NAME --name $COPILOT_DEPLOYMENT_STAGE --default-config --aws-access-key-id $AWS_ACCESS_KEY_ID --aws-secret-access-key $AWS_SECRET_ACCESS_KEY --aws-session-token $AWS_SESSION_TOKEN --region $AWS_DEFAULT_REGION

  # Init services
  copilot svc init -a $COPILOT_DEPLOYMENT_NAME --name traffic-comparator-jupyter
  copilot svc init -a $COPILOT_DEPLOYMENT_NAME --name traffic-comparator
  copilot svc init -a $COPILOT_DEPLOYMENT_NAME --name traffic-replayer

  copilot svc init -a $COPILOT_DEPLOYMENT_NAME --name elasticsearch
  copilot svc init -a $COPILOT_DEPLOYMENT_NAME --name capture-proxy
  copilot svc init -a $COPILOT_DEPLOYMENT_NAME --name opensearch-benchmark
fi


# Deploy env
copilot env deploy -a $COPILOT_DEPLOYMENT_NAME --name $COPILOT_DEPLOYMENT_STAGE

# Deploy services
copilot svc deploy -a $COPILOT_DEPLOYMENT_NAME --name traffic-comparator-jupyter --env $COPILOT_DEPLOYMENT_STAGE
copilot svc deploy -a $COPILOT_DEPLOYMENT_NAME --name traffic-comparator --env $COPILOT_DEPLOYMENT_STAGE
copilot svc deploy -a $COPILOT_DEPLOYMENT_NAME --name traffic-replayer --env $COPILOT_DEPLOYMENT_STAGE

copilot svc deploy -a $COPILOT_DEPLOYMENT_NAME --name elasticsearch --env $COPILOT_DEPLOYMENT_STAGE
copilot svc deploy -a $COPILOT_DEPLOYMENT_NAME --name capture-proxy --env $COPILOT_DEPLOYMENT_STAGE
copilot svc deploy -a $COPILOT_DEPLOYMENT_NAME --name opensearch-benchmark --env $COPILOT_DEPLOYMENT_STAGE


# Output deployment time
duration=$SECONDS
echo "Total deployment time: $((duration / 3600)) hour(s), $(((duration / 60) % 60)) minute(s) and $((duration % 60)) second(s) elapsed."