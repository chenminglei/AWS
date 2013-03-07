import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;


public class EC2Test2 {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws IOException, InterruptedException {
		Properties properties = new Properties();
		properties.load(EC2Test2.class.getResourceAsStream("/AwsCredentials.properties"));
		 
		BasicAWSCredentials bawsc = new BasicAWSCredentials(properties.getProperty("accessKey"), properties.getProperty("secretKey"));
		 
		//Create an Amazon EC2 Client
		AmazonEC2Client ec2 = new AmazonEC2Client(bawsc);
		 
		//Create Instance Request
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
		 
		//Configure Instance Request
		runInstancesRequest.withImageId("ami-2200904b")
		.withInstanceType("t1.micro")
		.withMinCount(1)
		.withMaxCount(1)
		.withKeyName("chenzhuokb2")
		.withSecurityGroups("default")
		.withMonitoring(true);
		 
		//Launch Instance
		RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);  
		 
		//Return the Object Reference of the Instance just Launched
		Instance instance=runInstancesResult.getReservation().getInstances().get(0);
		String instanceID = instance.getInstanceId();
		List<String> instanceIDS = new ArrayList<String>();
		instanceIDS.add(instanceID);
		
		
		DescribeInstanceStatusRequest request = new DescribeInstanceStatusRequest().withInstanceIds(instanceID);
		DescribeInstanceStatusResult result = ec2.describeInstanceStatus(request);
		List<InstanceStatus> state = result.getInstanceStatuses();
		while (state.size() < 1) { 
			try {
	            TimeUnit.SECONDS.sleep(3);
	        } catch (InterruptedException e) {
	            e.printStackTrace();
	        }
		    result = ec2.describeInstanceStatus(request);
		    state = result.getInstanceStatuses();
		}
		String status = state.get(0).getInstanceState().getName();
		System.out.println("********"+instance.getInstanceId() + ":" + status+ "****");
		System.out.println("****Waiting for the AMI to spin up****");
		
        TimeUnit.MINUTES.sleep(4);
		
		System.out.println("****Start to get CPUUtilization****");
		
		AmazonCloudWatchClient cw = new AmazonCloudWatchClient(bawsc);
		GetMetricStatisticsRequest getMetricRequest = new GetMetricStatisticsRequest()
		.withNamespace("AWS/EC2")
		.withPeriod(60)
		.withDimensions(new Dimension().withName("InstanceId").withValue(instanceID))
		.withMetricName("CPUUtilization")
		.withStatistics("Average");
		
		double tmpAver = 0.0;
		for (int i = 0; i < 6; i++){
			tmpAver = EC2Helper.GetCPUUtilization(cw, getMetricRequest);
			System.out.println("Round " + i + "is " + tmpAver);
			if (tmpAver > 0.75){
				System.out.println("Make a new instance");
				runInstancesResult = ec2.runInstances(runInstancesRequest);
				instance = runInstancesResult.getReservation().getInstances().get(0);
				String tmpinstanceID = instance.getInstanceId();
				instanceIDS.add(tmpinstanceID);
				
				request = new DescribeInstanceStatusRequest().withInstanceIds(tmpinstanceID);
				result = ec2.describeInstanceStatus(request);
				List<InstanceStatus> tmpstate = result.getInstanceStatuses();
				while (tmpstate.size() < 1) { 
					try {
			            TimeUnit.SECONDS.sleep(3);
			        } catch (InterruptedException e) {
			            e.printStackTrace();
			        }
					result = ec2.describeInstanceStatus(request);
					tmpstate = result.getInstanceStatuses();
				}				
				System.out.println("Make a new instance: " + tmpinstanceID);
			}
		}
		
		TerminateInstancesRequest terminateRequest = new TerminateInstancesRequest(instanceIDS);
	    ec2.terminateInstances(terminateRequest);
	}
}
