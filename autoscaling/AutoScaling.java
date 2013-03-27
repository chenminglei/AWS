import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.InstanceMonitoring;
import com.amazonaws.services.autoscaling.model.PutNotificationConfigurationRequest;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.EnableAlarmActionsRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest;



public class AutoScaling {

	/**
	 * @param args
	 * @return 
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	
	private static String SNSARN = "arn:aws:sns:us-east-1:606559959748:AutoScaling";
	private static String LaunchName = "autoconf";
	private static String GroupName = "autogroup";
	
	public static CreateLaunchConfigurationRequest launchConfig(AmazonAutoScalingClient asc){
		//Create Launch Configure Request 
		CreateLaunchConfigurationRequest launch_cfg = new CreateLaunchConfigurationRequest();
		launch_cfg.withLaunchConfigurationName(LaunchName)
		.withImageId("ami-92b82afb")
		.withInstanceType("m1.small")
		.withSecurityGroups("default");
		
		InstanceMonitoring instanceMonitoring = new InstanceMonitoring();
		instanceMonitoring.setEnabled(true);
		launch_cfg.setInstanceMonitoring(instanceMonitoring);
		
		return launch_cfg;
	}
	
	public static CreateAutoScalingGroupRequest autoGroup(AmazonAutoScalingClient asc){
		//Create Auto Group Request
		CreateAutoScalingGroupRequest autogroup_request = new CreateAutoScalingGroupRequest();
		autogroup_request.withAutoScalingGroupName(GroupName)
		.withMinSize(1)
		.withMaxSize(10)
		.withDesiredCapacity(4)
		.withLaunchConfigurationName(LaunchName)
		.withAvailabilityZones("us-east-1a");
		
		return autogroup_request;
	}
	
	public static ArrayList<String> policyRequest(AmazonAutoScalingClient asc){
		//Create Auto Group Policy
		PutScalingPolicyRequest policy_out = new PutScalingPolicyRequest();
		policy_out.withAutoScalingGroupName(GroupName)
		.withPolicyName("ScaleOut")
		.withAdjustmentType("ChangeInCapacity")
		.withScalingAdjustment(1);
				
		PutScalingPolicyRequest policy_in = new PutScalingPolicyRequest();
		policy_in.withAutoScalingGroupName(GroupName)
		.withPolicyName("ScaleIn")
		.withAdjustmentType("ChangeInCapacity")
		.withScalingAdjustment(-1);
	
		ArrayList<String> result = new ArrayList<String>();
		result.add(asc.putScalingPolicy(policy_out).getPolicyARN());
		result.add(asc.putScalingPolicy(policy_in).getPolicyARN());
		return result;
	}
	
	private static int getInstanceCount(String groupName, AmazonAutoScalingClient asc) {
		ArrayList<String> arrayGroup = new ArrayList<String>();
		arrayGroup.add(groupName);
		DescribeAutoScalingGroupsRequest describeAutoScalingGroupsRequest = new DescribeAutoScalingGroupsRequest();
		describeAutoScalingGroupsRequest.setAutoScalingGroupNames(arrayGroup);

		DescribeAutoScalingGroupsResult groupresult = asc
				.describeAutoScalingGroups(describeAutoScalingGroupsRequest);

		return groupresult.getAutoScalingGroups().get(0).getInstances().size();
	}
	
	public static void setNotification(AmazonAutoScalingClient asc) {
		ArrayList<String> notiArray = new ArrayList<String>();
		notiArray.add("autoscaling:EC2_INSTANCE_LAUNCH");
		notiArray.add("autoscaling:EC2_INSTANCE_TERMINATE");
		PutNotificationConfigurationRequest putNotificationConfigurationRequest = new PutNotificationConfigurationRequest()
		.withTopicARN(SNSARN)
		.withAutoScalingGroupName(GroupName)
		.withNotificationTypes(notiArray);
		asc.putNotificationConfiguration(putNotificationConfigurationRequest);
	}
	
	public static void setAlarm(ArrayList<String> result, AmazonCloudWatchClient cw){
		ArrayList<String> listout = new ArrayList<String>();
		listout.add(result.get(0));
		PutMetricAlarmRequest alarm_request_out = new PutMetricAlarmRequest()
        .withMetricName("CPUUtilization")
        .withNamespace("AWS/EC2")
        .withAlarmName("scale_out")
        .withActionsEnabled(true)
        .withStatistic("Average")
        .withThreshold(75.0)
        .withComparisonOperator("GreaterThanThreshold")
        .withPeriod(300)
        .withEvaluationPeriods(1)
        .withDimensions(new Dimension().withName("AutoScalingGroupName").withValue(GroupName))
		.withAlarmActions(listout);

		ArrayList<String> listin = new ArrayList<String>();
		listin.add(result.get(1));
		//listin.add(SNSAlarm);
		PutMetricAlarmRequest alarm_request_in = new PutMetricAlarmRequest()
        .withMetricName("CPUUtilization")
        .withNamespace("AWS/EC2")
        .withAlarmName("scale_in")
        .withActionsEnabled(true)
        .withStatistic("Average")
        .withThreshold(25.0)
        .withComparisonOperator("LessThanThreshold")
        .withPeriod(300)
        .withEvaluationPeriods(1)
        .withDimensions(new Dimension().withName("AutoScalingGroupName").withValue(GroupName))
		.withAlarmActions(listin);
		
		cw.putMetricAlarm(alarm_request_out);
		cw.putMetricAlarm(alarm_request_in);
		ArrayList<String> alarmlist = new ArrayList<String>();
		alarmlist.add(alarm_request_out.getAlarmName());
		alarmlist.add(alarm_request_in.getAlarmName());
		cw.enableAlarmActions(new EnableAlarmActionsRequest().withAlarmNames(alarmlist));
	}
	
	
	public static void main(String[] args) throws IOException, InterruptedException {
		Properties properties = new Properties();
		properties.load(AutoScaling.class.getResourceAsStream("/AwsCredentials.properties"));
		BasicAWSCredentials bawsc = new BasicAWSCredentials(properties.getProperty("accessKey"), properties.getProperty("secretKey"));
		 
		//Create an Amazon EC2 Client
		AmazonAutoScalingClient asc = new AmazonAutoScalingClient(bawsc);
		AmazonCloudWatchClient cw = new AmazonCloudWatchClient(bawsc);
		
		CreateLaunchConfigurationRequest launch_cfg = launchConfig(asc);
		asc.createLaunchConfiguration(launch_cfg);
		
		CreateAutoScalingGroupRequest autogroup_request = autoGroup(asc);
		asc.createAutoScalingGroup(autogroup_request);
		
		int num = 0;
		while((num = getInstanceCount(GroupName, asc))!= 4){
			Thread.sleep(1000 * 60);
		}
		System.out.println("****autogroup create successfully****");
		
		System.out.println("****Start to create alarm****");
		ArrayList<String> result = policyRequest(asc);
		setAlarm(result, cw);
		
		System.out.println("****Start to notify****");
		setNotification(asc);
	}
}
