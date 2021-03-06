package com.sumologic.jenkins.jenkinssumologicplugin.sender;

import com.google.gson.Gson;
import com.sumologic.jenkins.jenkinssumologicplugin.PluginDescriptorImpl;
import com.sumologic.jenkins.jenkinssumologicplugin.constants.LogTypeEnum;
import com.sumologic.jenkins.jenkinssumologicplugin.model.BuildModel;
import com.sumologic.jenkins.jenkinssumologicplugin.model.PipelineStageModel;
import com.sumologic.jenkins.jenkinssumologicplugin.model.TestCaseModel;
import com.sumologic.jenkins.jenkinssumologicplugin.model.TestCaseResultModel;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.sumologic.jenkins.jenkinssumologicplugin.constants.SumoConstants.*;

/**
 * Sumo Logic plugin for Jenkins model.
 * <p>
 * Log Sender Helper
 * <p>
 * Created by Sourabh Jain on 5/2019.
 */
@SuppressFBWarnings("DM_DEFAULT_ENCODING")
public class LogSenderHelper {

    public final static Logger LOG = Logger.getLogger(LogSenderHelper.class.getName());

    public LogSenderHelper() {
        LOG.log(Level.INFO, "Initialized the Log Sender Helper");
    }

    private static class LogSenderHelperHolder {
        public static LogSenderHelper logSenderHelper = new LogSenderHelper();
    }

    public static LogSenderHelper getInstance() {
        return LogSenderHelperHolder.logSenderHelper;
    }

    public void sendData(byte[] bytes){
        LogSender.getInstance().sendLogs(bytes);
    }

    public void sendDataWithFields(byte[] bytes, HashMap<String, String> fields){
        LogSender.getInstance().sendLogs(bytes, null, fields);
    }

    public void sendLogsToPeriodicSourceCategory(String data) {

        PluginDescriptorImpl pluginDescriptor = PluginDescriptorImpl.getInstance();
        if (pluginDescriptor.isPeriodicLogEnabled()) {
            LogSender.getInstance().sendLogs(data.getBytes());
        }
    }

    public void sendMultiplePeriodicLogs(final List<String> messages) {
        List<String> strings = divideDataIntoEquals(messages);
        for (String data : strings) {
            sendLogsToPeriodicSourceCategory(data);
        }
    }

    public void sendFilesData(final List<String> messages, String localFileString, HashMap<String, String> fields) {
        if (CollectionUtils.isNotEmpty(messages)) {
            List<String> strings = divideDataIntoEquals(messages);
            for (String data : strings) {
                LogSender.getInstance().sendLogs(data.getBytes(), localFileString, fields);
            }
        }
    }

    public void sendLogsToMetricDataCategory(final List<String> messages) {
        PluginDescriptorImpl pluginDescriptor = PluginDescriptorImpl.getInstance();
        if (pluginDescriptor.isMetricDataEnabled()) {
            List<String> strings = divideDataIntoEquals(messages);
            for (String data : strings) {
                LogSender.getInstance().sendLogs(data.getBytes(), null, GRAPHITE_CONTENT_TYPE);
            }
        }

    }

    public void sendJobStatusLogs(String data) {
        LogSender.getInstance().sendLogs(data.getBytes());
    }

    public void sendConsoleLogs(String data, String jobName, int buildNumber, String stageName) {

        String sourceName = jobName + "#" + buildNumber;
        if (StringUtils.isNotEmpty(stageName)) {
            sourceName = sourceName + "#" + stageName;
        }
        LogSender.getInstance().sendLogs(data.getBytes(), sourceName);
    }

    public void sendAuditLogs(String data) {
        PluginDescriptorImpl pluginDescriptor = PluginDescriptorImpl.getInstance();
        if (pluginDescriptor.isAuditLogEnabled()) {
            LogSender.getInstance().sendLogs(data.getBytes());
        }
    }

    private static List<String> divideDataIntoEquals(final List<String> messages) {
        List<String> convertedMessages = new ArrayList<>();

        StringBuilder stringBuilder = new StringBuilder();
        int count = 1;
        for (String message : messages) {
            stringBuilder.append(message).append("\n");
            if (count % DIVIDER_FOR_MESSAGES == 0) {
                convertedMessages.add(stringBuilder.toString());
                stringBuilder = new StringBuilder();
            }
            count++;
        }
        convertedMessages.add(stringBuilder.toString());

        return convertedMessages;
    }

    public static void sendTestResult(TestCaseModel testCaseModel, BuildModel buildModel) {
        Gson gson = new Gson();

        Map<String, Object> data = new HashMap<>();

        data.put("logType", LogTypeEnum.TEST_RESULT.getValue());
        data.put("name", buildModel.getName());
        data.put("number", buildModel.getNumber());

        if (testCaseModel != null) {
            if (CollectionUtils.isNotEmpty(testCaseModel.getTestResults())) {
                List<TestCaseResultModel> testResults = testCaseModel.getTestResults();
                // send test cases based on the size
                List<TestCaseResultModel> toBeSent = new LinkedList<>();
                data.put("testResult", toBeSent);
                int size = gson.toJson(data).getBytes().length;
                for (TestCaseResultModel testCaseResultModel : testResults) {
                    if ("Failed".equals(testCaseResultModel.getStatus())) {
                        testCaseResultModel.setErrorDetails(format(testCaseResultModel.getErrorDetails()));
                        testCaseResultModel.setErrorStackTrace(format(testCaseResultModel.getErrorStackTrace()));
                    }
                    size = size + gson.toJson(testCaseResultModel).getBytes().length;
                    if (size > MAX_DATA_SIZE) {
                        sendTestResultInChunksOfPreDefinedSize(buildModel, gson, toBeSent, data);
                        toBeSent.clear();
                    }
                    toBeSent.add(testCaseResultModel);
                    size = gson.toJson(data).getBytes().length;
                }
                if (CollectionUtils.isNotEmpty(toBeSent)) {
                    sendTestResultInChunksOfPreDefinedSize(buildModel, gson, toBeSent, data);
                    toBeSent.clear();
                }
            }
        }
    }

    private static void sendTestResultInChunksOfPreDefinedSize(BuildModel buildModel, Gson gson,
                                                               List<TestCaseResultModel> toBeSent, Map<String, Object> data) {
        LOG.log(Level.INFO, "Job Name - " + buildModel.getName() + ", Build Number - " + buildModel.getNumber() + ", test result count is " + toBeSent.size() +
                ", number of bytes is " + gson.toJson(data).getBytes().length);
        LogSender.getInstance().sendLogs(gson.toJson(data).getBytes());
    }

    public static void sendPipelineStages(List<PipelineStageModel> stages, BuildModel buildModel) {
        List<String> allStages = new ArrayList<>();
        Gson gson = new Gson();
        Map<String, Object> data = new HashMap<>();

        data.put("logType", LogTypeEnum.PIPELINE_STAGES.getValue());
        data.put("name", buildModel.getName());
        data.put("number", buildModel.getNumber());
        // Adding extra data like Build status, Build run time, Build URL, UpStream URL, Job Type.
        data.put("result", buildModel.getResult());
        data.put("jobStartTime", buildModel.getJobStartTime());
        data.put("jobType", buildModel.getJobType());
        data.put("jobRunDuration", buildModel.getJobRunDuration());
        data.put("jobBuildURL", buildModel.getJobBuildURL());
        data.put("upstreamJobURL", buildModel.getUpstreamJobURL());
        if (CollectionUtils.isNotEmpty(stages)) {
            for (PipelineStageModel pipelineStageModel : stages) {
                data.put("stages", new ArrayList<>(Collections.singletonList(pipelineStageModel)));
                allStages.add(gson.toJson(data));
            }
        }
        List<String> strings = divideDataIntoEquals(allStages);
        for (String value : strings) {
            sendStagesInChunksOfPreDefinedSize(buildModel, allStages, value);
        }
    }

    private static void sendStagesInChunksOfPreDefinedSize(BuildModel buildModel, List<String> toBeSent, String data) {
        LOG.log(Level.INFO, "Job Name - " + buildModel.getName() + ", Build Number - " + buildModel.getNumber() + ", Stage count is " + toBeSent.size() +
                ", number of bytes is " + data.length());
        LogSender.getInstance().sendLogs(data.getBytes());
    }

    private static String format(String data) {
        if (StringUtils.isNotEmpty(data)) {
            data = data.replace("{", "(");
            return data.replace("}", ")");
        }
        return null;
    }
}
