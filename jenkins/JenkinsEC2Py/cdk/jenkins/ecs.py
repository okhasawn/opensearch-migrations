from aws_cdk import (
    aws_ecs as ecs,
    aws_ec2 as ec2,
    aws_efs as efs,
    Stack
)
from constructs import Construct
from configparser import ConfigParser

config = ConfigParser()
config.read('config.ini')


class ECSCluster(Stack):
    def __init__(self, scope: Construct, id: str, vpc, service_discovery_namespace, **kwargs):
        super().__init__(scope, id, **kwargs)

        self.vpc = vpc
        self.service_discovery_namespace = service_discovery_namespace

        # Create ECS cluster
        self.cluster = ecs.Cluster(
            self, "ECSCluster",
            vpc=self.vpc,
            default_cloud_map_namespace=ecs.CloudMapNamespaceOptions(name=service_discovery_namespace)
        )

        if config['DEFAULT']['ec2_enabled'] == "yes":
            self.asg = self.cluster.add_capacity(
                "Ec2",
                instance_type=ec2.InstanceType(config['DEFAULT']['instance_type']),
                key_name="jenkinsonaws",
                # ... other properties ...
            )

            self.efs_sec_grp = ec2.SecurityGroup(
                self, "EFSSecGrp",
                vpc=self.vpc,
                allow_all_outbound=True,
            )

            self.efs_sec_grp.add_ingress_rule(
                peer=ec2.Peer.security_group_id(self.asg.connections.security_groups[0].security_group_id),
                connection=ec2.Port.tcp(2049),
                description="EFS"
            )

            self.efs_filesystem = efs.CfnFileSystem(
                self, "EFSBackend",
            )

            counter = 0
            for subnet in self.vpc.private_subnets:
                efs.CfnMountTarget(
                    self, f"EFS{counter}",
                    file_system_id=self.efs_filesystem.ref,
                    subnet_id=subnet.subnet_id,
                    security_groups=[self.efs_sec_grp.security_group_id]
                )
                counter += 1

            self.user_data = """
                sudo yum install -y amazon-efs-utils
                sudo mkdir /mnt/efs
                sudo chown -R ec2-user: /mnt/efs
                sudo chmod -R 0777 /mnt/efs
                sudo mount -t efs -o tls /mnt/efs {}:/ efs
                """.format(self.efs_filesystem.ref)

            self.asg.add_user_data(self.user_data)
