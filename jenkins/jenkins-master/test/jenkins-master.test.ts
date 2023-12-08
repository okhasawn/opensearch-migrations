import { Template } from 'aws-cdk-lib/assertions';
import { App } from 'aws-cdk-lib';
import { JenkinsMasterStack } from '../lib/jenkins-master-stack';

test('Empty Stack', () => {
    const app = new App();

    const stack = new JenkinsMasterStack(app, 'MigrationsTestStack');

    const template = Template.fromStack(stack);

    template.resourceCountIs('AWS::CDK::Metadata', 0);
});
