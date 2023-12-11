import * as ec2 from 'aws-cdk-lib/aws-ec2';
import { aws_ecr as ecr, aws_ecr_assets as ecr_assets } from 'aws-cdk-lib';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as cdk from 'aws-cdk-lib';

interface JenkinsWorkerProps extends cdk.StackProps {
  vpc: ec2.IVpc,
}

export class JenkinsWorker extends cdk.Stack {
  public readonly containerImage: ecr_assets.DockerImageAsset;
  public readonly workerSecurityGroup: ec2.SecurityGroup;
  public readonly workerExecutionRole: iam.Role;
  public readonly workerTaskRole: iam.Role;
  public readonly workerLogsGroup: logs.ILogGroup;
  public readonly workerLogStream: logs.LogStream;


  constructor(scope: cdk.App, id: string, props: JenkinsWorkerProps) {
    super(scope, id, props);

    const vpc = props.vpc;

    this.containerImage = new ecr_assets.DockerImageAsset(this, "JenkinsWorkerDockerImage", {
      directory: '../docker/worker/'
    });

    this.workerSecurityGroup = new ec2.SecurityGroup(this, "WorkerSecurityGroup", {
      securityGroupName: "jenkins-integ-worker-sg",
      vpc: vpc,
      description: "Jenkins Worker access to Jenkins Master",
    });

    this.workerExecutionRole = new iam.Role(this, "WorkerExecutionRole", {
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
    })
    this.workerExecutionRole.addManagedPolicy(
      iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AmazonECSTaskExecutionRolePolicy')
    );

    this.workerTaskRole = new iam.Role(this, "WorkerTaskRole", {
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
    });

    this.workerLogsGroup = new logs.LogGroup(this, 'WorkerLogsGroup', {
      logGroupName: '/ecs/jenkins-production-worker',
      retention: logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    this.workerLogStream = new logs.LogStream(this, "WorkerLogStream", {
      logStreamName: "jenkins-production-worker",
      logGroup: this.workerLogsGroup
    });
  }
}
