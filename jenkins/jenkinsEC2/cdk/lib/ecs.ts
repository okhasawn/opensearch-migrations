import { aws_servicediscovery as sd, aws_ecs as ecs, aws_ec2 as ec2, aws_autoscaling as autoscaling, aws_efs as efs } from 'aws-cdk-lib';
import { Stack, StackProps } from 'aws-cdk-lib';
import { Construct } from 'constructs';

interface EcsProps extends StackProps {
  vpc: ec2.IVpc,
  serviceDiscoveryNamespace: string,
}

export class Ecs extends Stack {
  public readonly cluster: ecs.Cluster;
  public readonly asg: autoscaling.AutoScalingGroup;
  public readonly efsSecGrp: ec2.SecurityGroup;
  public readonly efsFilesystem: efs.CfnFileSystem;

  constructor(scope: Construct, id: string, props: EcsProps) {
    super(scope, id, props);

    const serviceDiscoveryNamespace = props.serviceDiscoveryNamespace;
    const vpc = props.vpc;

    // ECS Cluster
    this.cluster = new ecs.Cluster(this, "EcsCluster", {
      clusterName: 'jenkins-production',
      vpc: vpc,
      defaultCloudMapNamespace: {
        name: serviceDiscoveryNamespace,
        type: sd.NamespaceType.DNS_PRIVATE,
      }
    });

    // EC2
    const asg = this.cluster.addCapacity("Ec2", {
      instanceType: new ec2.InstanceType('t3.xlarge'),
      keyName: "jenkins-prod-key",
      allowAllOutbound: true,
      associatePublicIpAddress: true,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC }
    });

    // EFS Security Group
    const efsSecGrp = new ec2.SecurityGroup(this, "EFSSecGrp", {
      securityGroupName: "jenkins-prod-efs-sg",
      vpc: vpc,
      allowAllOutbound: true,
    });
    efsSecGrp.addIngressRule(
        this.cluster.connections.securityGroups[0],
        new ec2.Port({
          protocol: ec2.Protocol.ALL,
          stringRepresentation: "ALL",
          fromPort: 2049,
          toPort: 2049,
        }),
        "EFS"
    );

    // EFS Filesystem
    const efsFilesystem = new efs.CfnFileSystem(this, "EFSBackend");
    vpc.privateSubnets.forEach((subnet, idx) => {
      new efs.CfnMountTarget(this, `EFS${idx}`, {
        fileSystemId: efsFilesystem.ref,
        subnetId: subnet.subnetId,
        securityGroups: [efsSecGrp.securityGroupId]
      });
    });

    // User Data
    const userData = `
sudo yum install -y amazon-efs-utils
sudo mkdir /mnt/efs
sudo chown -R ec2-user: /mnt/efs
sudo chmod -R 0777 /mnt/efs
sudo mount -t efs -o tls /mnt/efs ${efsFilesystem.ref}:/ efs`;
    asg.addUserData(userData);
  }
}
