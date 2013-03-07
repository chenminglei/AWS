import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;


public class EC2Helper {
	
	public static long offsetInMilliseconds = 1000 * 60;
	
	
	public static double GetCPUUtilization(AmazonCloudWatchClient cw, GetMetricStatisticsRequest getMetricRequest)
			throws InterruptedException{
		double avgCPUUtilization = 0;
		for (int i = 0; i < 5; i++){
			getMetricRequest.setStartTime(new Date(new Date().getTime() - offsetInMilliseconds));
			getMetricRequest.setEndTime(new Date());
			GetMetricStatisticsResult getMetricResult = cw.getMetricStatistics(getMetricRequest);
		
			List dataPoint = getMetricResult.getDatapoints();
			if (dataPoint.size() >= 1){
				avgCPUUtilization += ((Datapoint) dataPoint.get(0)).getAverage();
				System.out.println(i+": instance's average CPU utilization : " + ((Datapoint) dataPoint.get(0)).getAverage()
						+ " " + ((Datapoint) dataPoint.get(0)).getTimestamp());
			} else { 
				i--;
			}
			TimeUnit.MINUTES.sleep(1);
		}
		return avgCPUUtilization/5;
	}
}
