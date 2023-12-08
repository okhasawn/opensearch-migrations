import {Stack, StackProps, Duration, CfnOutput, RemovalPolicy} from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as efs from 'aws-cdk-lib/aws-efs';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as route53 from 'aws-cdk-lib/aws-route53';
import {Port} from "aws-cdk-lib/aws-ec2";

export class JenkinsMasterStack extends Stack {
    constructor(scope: Construct, id: string, props?: StackProps) {
        super(scope, id, props);

        const cluster = new ecs.Cluster(this, 'jenkins-cluster', {
            clusterName: 'jenkins-cluster'
        });

        const vpc = cluster.vpc;

        const fileSystem = new efs.FileSystem(this, 'JenkinsFileSystem', {
            vpc: vpc,
            removalPolicy: RemovalPolicy.DESTROY
        });

        const accessPoint = fileSystem.addAccessPoint('AccessPoint', {
            path: '/jenkins-home',
            posixUser: {
                uid: '1000',
                gid: '1000',
            },
            createAcl: {
                ownerGid: '1000',
                ownerUid: '1000',
                permissions: '755'
            }
        });

        const taskDefinition = new ecs.FargateTaskDefinition(this, 'jenkins-task-definition', {
            memoryLimitMiB: 8192,
            cpu: 4096,
            family: 'jenkins'
        });

        taskDefinition.addVolume({
            name: 'jenkins-home',
            efsVolumeConfiguration: {
                fileSystemId: fileSystem.fileSystemId,
                transitEncryption: 'ENABLED',
                authorizationConfig: {
                    accessPointId: accessPoint.accessPointId,
                    iam: 'ENABLED'
                }
            }
        });

        const containerDefinition = taskDefinition.addContainer('jenkins', {
            image: ecs.ContainerImage.fromRegistry("jenkins/jenkins:lts"),
            logging: ecs.LogDrivers.awsLogs({streamPrefix: 'jenkins'}),
        });

        containerDefinition.addPortMappings({
            containerPort: 8080
        });

        containerDefinition.addMountPoints({
            containerPath: '/var/jenkins_home',
            sourceVolume: 'jenkins-home',
            readOnly: false
        });

        const service = new ecs.FargateService(this, 'JenkinsService', {
            cluster,
            taskDefinition,
            desiredCount: 1,
            maxHealthyPercent: 100,
            minHealthyPercent: 0,
            healthCheckGracePeriod: Duration.minutes(5)
        });
        service.connections.allowTo(fileSystem, Port.tcp(2049));



        const loadBalancer = new elbv2.ApplicationLoadBalancer(this, 'LoadBalancer', {vpc, internetFacing: true});
        new CfnOutput(this, 'LoadBalancerDNSName', {value: loadBalancer.loadBalancerDnsName});

        const listener = loadBalancer.addListener('Listener', {
            port: 80
        });
        listener.addTargets('JenkinsTarget', {
            port: 8080,
            targets: [service],
            deregistrationDelay: Duration.seconds(10),
            healthCheck: {
                path: '/login'
            }
        });

        const hostedZoneName = this.node.tryGetContext('hostedZoneName')
        if (hostedZoneName) {
            const hostedZone = route53.HostedZone.fromLookup(this, 'HostedZone', {
                domainName: hostedZoneName
            });
            new route53.CnameRecord(this, 'CnameRecord', {
                zone: hostedZone,
                recordName: 'jenkins',
                domainName: loadBalancer.loadBalancerDnsName,
                ttl: Duration.minutes(1)
            });
        }
    }
}

