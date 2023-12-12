from aws_cdk import aws_ec2
from aws_cdk import Stack
from constructs import Construct
from configparser import ConfigParser

config = ConfigParser()
config.read('config.ini')


class Network(Stack):

    def __init__(self, scope: Construct, id: str, **kwargs):
        super().__init__(scope, id, **kwargs)

        self.vpc = aws_ec2.Vpc(
            self, "Vpc",
            cidr=config['DEFAULT']['cidr'],
        )
