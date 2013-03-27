import java.util.Date;
import java.util.List;

import com.amazonaws.auth.ClasspathPropertiesFileCredentialsProvider;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyResult;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
//479026016592 ARN#

public class AutoScaling {

	public static long MINUTE = 60000;
	/**
	 * launch an instance
	 * @return the instance result
	 */
	public static RunInstancesResult launchInstance(){
		//Create an Amazon EC2 Client
		AmazonEC2Client ec2 = new AmazonEC2Client(new ClasspathPropertiesFileCredentialsProvider());


		//Create Instance Request
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

		//Configure Instance Request
		runInstancesRequest.withImageId("ami-92b82afb")
		.withInstanceType("m1.small")
		.withMinCount(1)
		.withMaxCount(10)
		.withKeyName("danleiz")
		.withSecurityGroups("default")
		.withMonitoring(true);

		//Launch Instance
		RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest); 
		return runInstancesResult;
	}
	
    public static void createAutoScalingGroup() throws InterruptedException{
		AmazonEC2Client ec2Client = new AmazonEC2Client(new ClasspathPropertiesFileCredentialsProvider());
		AmazonAutoScalingClient amazonAutoScalingClient = new AmazonAutoScalingClient(new ClasspathPropertiesFileCredentialsProvider());
		AmazonCloudWatchClient cwClient= new AmazonCloudWatchClient(new ClasspathPropertiesFileCredentialsProvider());

		CreateLaunchConfigurationRequest confRequest = new CreateLaunchConfigurationRequest();

		confRequest.withImageId("ami-92b82afb")
		.withInstanceType("m1.small")
		.withLaunchConfigurationName("conf1")
		.withKeyName("danleiz")
		.withSecurityGroups("default");
		//System.out.println(confRequest.toString());



		CreateAutoScalingGroupRequest group = new CreateAutoScalingGroupRequest();
		group.withMinSize(1)
		.withMaxSize(10)
		.withDesiredCapacity(4)
		.withAutoScalingGroupName("group2")
		.withLaunchConfigurationName("conf1")
		.withAvailabilityZones("us-east-1d");


		//amazonAutoScalingClient.createLaunchConfiguration(confRequest);
		//amazonAutoScalingClient.createAutoScalingGroup(group);


		PutScalingPolicyRequest policyOutRequest = new PutScalingPolicyRequest();
		policyOutRequest.withAutoScalingGroupName("group2")
		.withPolicyName("ScaleOut")
		.withAdjustmentType("ChangeInCapacity")
		.withScalingAdjustment(1);

		PutScalingPolicyRequest policyInRequest = new PutScalingPolicyRequest();
		policyInRequest.withAutoScalingGroupName("group2")
		.withPolicyName("ScaleIn")
		.withAdjustmentType("ChangeInCapacity")
		.withScalingAdjustment(-1);

		System.out.println("Starting cloud watch");
		
		String ScaleOut = amazonAutoScalingClient.putScalingPolicy(policyOutRequest).getPolicyARN();
		String ScaleIn = amazonAutoScalingClient.putScalingPolicy(policyInRequest).getPolicyARN();
		
		AmazonSNSClient snsClient = new AmazonSNSClient(new ClasspathPropertiesFileCredentialsProvider());

		CreateTopicRequest createTopicRequest = new CreateTopicRequest()
		.withName("AutoScaling");
		CreateTopicResult snsResult = snsClient.createTopic(createTopicRequest);
		String SNSAlarm = snsResult.getTopicArn();
		
		PutMetricAlarmRequest alarmRequestScaleOut = new PutMetricAlarmRequest()
		.withMetricName("CPUUtilization")
		.withNamespace("AWS/EC2")
		.withAlarmName("AWS autoscale")
		.withActionsEnabled(true)
		.withStatistic("Average")
		.withThreshold(75.0)
		.withComparisonOperator("GreaterThanThreshold")
		.withPeriod(300)
		.withEvaluationPeriods(1)
		.withDimensions(new Dimension().withName("AutoScalingGroupName").withValue("group2"))
		.withAlarmName("Scale Out")
		.withAlarmActions(ScaleOut)
		.withAlarmActions(SNSAlarm);

		

		
		PutMetricAlarmRequest alarmRequestScaleIn = new PutMetricAlarmRequest()
		.withMetricName("CPUUtilization")
		.withNamespace("AWS/EC2")
		.withAlarmName("AWS autoscale")
		.withActionsEnabled(true)
		.withStatistic("Average")
		.withThreshold(75.0)
		.withComparisonOperator("LessThanThreshold")
		.withPeriod(300)
		.withEvaluationPeriods(1)
		.withDimensions(new Dimension().withName("AutoScalingGroupName").withValue("group2"))
		.withAlarmName("Scale In")
		.withAlarmActions(ScaleIn)
		.withAlarmActions(SNSAlarm);
		
		cwClient.putMetricAlarm(alarmRequestScaleOut);
		cwClient.putMetricAlarm(alarmRequestScaleIn);



	}

	public static double cloudwatch(AmazonCloudWatchClient cwClient,List<String> instanceIDs) throws InterruptedException{
		double result = 0;


		GetMetricStatisticsRequest cloudwatchRequest = new GetMetricStatisticsRequest();

		for(String instanceID : instanceIDs){
			cloudwatchRequest.withDimensions(new Dimension().withName("InstanceId").withValue(instanceID))
			.withPeriod(60)
			.withMetricName("CPUUtilization")
			.withNamespace("AWS/EC2")
			.withStatistics("Average");


			for(int i = 0;i < 5;i++){

				Date start = new Date();
				Date end = new Date();
				end.setTime(end.getTime());
				start.setTime(end.getTime() - 1*MINUTE);
				cloudwatchRequest.withStartTime(start)
				.withEndTime(end);

				GetMetricStatisticsResult cloudwatchResult = cwClient.getMetricStatistics(cloudwatchRequest);
				List<Datapoint> data = cloudwatchResult.getDatapoints();

				if(data.isEmpty()){
					i--;
					continue;
				}

				result += data.get(0).getAverage();
				System.out.println(instanceID+"'s result "+i+" is: "+result);


				Thread.sleep(1*MINUTE);

			}
		}



		return result/5/instanceIDs.size();


	}


	public static void main(String[] args) throws InterruptedException {
		createAutoScalingGroup();
	}

}



