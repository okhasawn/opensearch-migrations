import {Template} from 'aws-cdk-lib/assertions';
import * as cdk from 'aws-cdk-lib';
import { Network } from '../lib/network';

test('Vpc Created', () => {
    const app = new cdk.App();
    const stack = new Network(app, 'MyTestStack');
    const template = Template.fromStack(stack);

    template.hasResourceProperties("AWS::EC2::VPC", {
        CidrBlock: '10.0.0.0/24'
    });
});
