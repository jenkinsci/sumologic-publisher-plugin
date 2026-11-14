package com.sumologic.jenkins.jenkinssumologicplugin.pipeline;

import com.sumologic.jenkins.jenkinssumologicplugin.model.BuildModel;
import com.sumologic.jenkins.jenkinssumologicplugin.model.PipelineStageModel;
import com.sumologic.jenkins.jenkinssumologicplugin.sender.LogSenderHelper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.console.AnnotatedLargeText;
import hudson.model.Result;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.LabelledChunkFinder;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.sumologic.jenkins.jenkinssumologicplugin.constants.SumoConstants.DATETIME_FORMATTER;
import static com.sumologic.jenkins.jenkinssumologicplugin.constants.SumoConstants.JENKINS_MAIN;
import static com.sumologic.jenkins.jenkinssumologicplugin.sender.LogSenderHelper.sendPipelineStages;

@SuppressFBWarnings("RV_RETURN_VALUE_IGNORED")
public class PipelineStageViewExtractor {
    private static final LogSenderHelper logSenderHelper = LogSenderHelper.getInstance();
    private static final Logger LOG = Logger.getLogger(PipelineStageViewExtractor.class.getName());

    /**
     * This method identifies all the stages as mentioned in the pipeline-stage-view plugin.
     *
     * @param workflowRun - pipeline run
     * @param buildModel  - the jenkins plugin build model
     */
    public static void extractPipelineStages(WorkflowRun workflowRun, BuildModel buildModel) {
        List<PipelineStageModel> pipelineStageModels = getPipelineStages(workflowRun);
        if (CollectionUtils.isNotEmpty(pipelineStageModels)) {
            sendPipelineStages(pipelineStageModels, buildModel);
        }
    }

    static List<PipelineStageModel> getPipelineStages(WorkflowRun workflowRun) {
        List<PipelineStageModel> pipelineStageModels = new ArrayList<>();
        FlowExecution execution = workflowRun.getExecution();
        if (execution == null) {
            return pipelineStageModels;
        }
        ExecutionNodeExtractor visitor = new ExecutionNodeExtractor();
        ForkScanner.visitSimpleChunks(execution.getCurrentHeads(), visitor, new LabelledChunkFinder());

        for (PipelineStageCollector.Node stage : PipelineStageCollector.collectStages(execution)) {
            List<String> steps = new ArrayList<>();
            PipelineStageModel pipelineStageModel = setStageDetails(stage, visitor.getWorkspaceNodes());
            stage.steps.forEach(step -> {
                String data = setStepDetails(step, visitor.getWorkspaceNodes());
                if (StringUtils.isNotBlank(data)) {
                    steps.add(data);
                }
            });
            if (CollectionUtils.isNotEmpty(steps)) {
                pipelineStageModel.setSteps(steps);
            }
            String stageId = stage.node.getId();
            if (visitor.getParallelNodes().containsKey(stageId)) {
                pipelineStageModel.setParallelStage(visitor.getParallelNodes().get(stageId));
            }
            pipelineStageModels.add(pipelineStageModel);
        }
        AtomicInteger counter = new AtomicInteger(1);
        pipelineStageModels.forEach(pipelineStageModel -> {
            pipelineStageModel.setId(counter.getAndIncrement());
        });
        return pipelineStageModels;
    }

    private static PipelineStageModel setStageDetails(PipelineStageCollector.Node stage, Map<String, String> executionNodes) {
        final PipelineStageModel pipelineStageDTO = new PipelineStageModel();
        FlowNode node = stage.node;
        pipelineStageDTO.setStageId(node.getId());
        pipelineStageDTO.setName(node.getDisplayName());
        pipelineStageDTO.setStatus(stage.status);
        pipelineStageDTO.setStartTime(DATETIME_FORMATTER.format(stage.startTime));
        pipelineStageDTO.setDuration(stage.duration / 1000f);
        pipelineStageDTO.setPauseDuration(stage.pause / 1000f);
        pipelineStageDTO.setArguments(ArgumentsAction.getStepArgumentsAsString(node));
        pipelineStageDTO.setExecutionNode(executionNodes.getOrDefault(node.getId(), JENKINS_MAIN));

        ErrorAction error = stage.error;
        if (error != null) {
            String errorMessage;
            errorMessage = "StageErrorType - " + errorType(error) + "," + "StageErrorMessage - " + errorMessage(error) + "";
            errorMessage = errorMessage.replace("{", "(");
            errorMessage = errorMessage.replace("}", ")");
            pipelineStageDTO.setError(errorMessage);
        }
        return pipelineStageDTO;
    }

    private static String setStepDetails(PipelineStageCollector.Node step, Map<String, String> executionNodes) {
        StringBuilder stringBuilder = new StringBuilder();
        FlowNode node = step.node;

        stringBuilder.append("StepName - ").append(node.getDisplayName()).append(",")
                .append("StepStatus - ").append(step.status).append(",")
                .append("StepDuration - ").append(step.duration / 1000f).append(",")
                .append("StepArguments - ").append(ArgumentsAction.getStepArgumentsAsString(node)).append(",")
                .append("StepExecutedOn - ").append(executionNodes.getOrDefault(node.getId(), JENKINS_MAIN));

        ErrorAction error = step.error;
        if (error != null) {
            stringBuilder.append(",StepErrorType - ").append(errorType(error)).append(",")
                    .append("StepErrorMessage - ").append(errorMessage(error));
        }

        stringBuilder.append(",StepPauseDuration - ").append(step.pause / 1000f).append(",")
                .append("StepId - ").append(node.getId());

        String stepDetails = stringBuilder.toString();
        stepDetails = stepDetails.replace("{", "(");
        stepDetails = stepDetails.replace("}", ")");
        return stepDetails;
    }

    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
            justification = "Deserialization can leave a null Throwable")
    private static String errorType(ErrorAction error) {
        Throwable throwable = error.getError();
        if (throwable == null) {
            // rare cases, for example serialization problems
            return "No type: null Throwable on error";
        }
        return throwable.getClass().getName();
    }

    @SuppressFBWarnings(value = {"RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", "DCN_NULLPOINTER_EXCEPTION"},
            justification = "Deserialization can leave a null Throwable, GROOVY-8936 is fixed only in Groovy 4")
    private static String errorMessage(ErrorAction error) {
        Throwable throwable = error.getError();
        if (throwable == null) {
            // rare cases, for example serialization problems
            return "No message: null Throwable on error";
        }
        try {
            return throwable.getMessage();
        } catch (NullPointerException npe) {
            // GROOVY-8936
            return "No message: NullPointerException on .getMessage()";
        }
    }

    /**
     * This method sends the console logs with source name as the stage/step name to identify the console logs
     * with respect to stage/step of the pipeline.
     *
     * @param workflowRun - pipeline run
     */
    public static void extractConsoleLogs(WorkflowRun workflowRun) {
        getConsoleLogs(workflowRun).forEach((nodeName, messages) -> {
            StringBuilder stringBuilder = new StringBuilder();
            messages.forEach(s -> {
                stringBuilder.append("[").append(DATETIME_FORMATTER.format(new Date()))
                        .append("] ").append(" ")
                        .append(s).append("\n");
            });
            logSenderHelper.sendConsoleLogs(stringBuilder.toString(), workflowRun.getParent().getFullName(), workflowRun.getNumber(), nodeName);
        });
    }

    /**
     * @return the console log messages of the run, keyed by the name of the stage or parallel branch they belong to
     */
    static Map<String, Set<String>> getConsoleLogs(WorkflowRun workflowRun) {
        Map<String, Set<String>> logsNodeWise = new HashMap<>();

        FlowExecution exec = workflowRun.getExecution();
        if (exec != null) {
            Map<String, String> nodeWithNames = PipelineStageCollector.collectLabels(exec);
            FlowGraphWalker walker = new FlowGraphWalker(exec);
            for (FlowNode node : walker) {
                try {
                    LogAction action = node.getAction(LogAction.class);
                    LinkedHashSet<String> messages = new LinkedHashSet<>();
                    if (action != null && action.getLogText() != null) {
                        AnnotatedLargeText<? extends FlowNode> logText = action.getLogText();
                        StringWriter writer = new StringWriter();

                        logText.writeLogTo(0, writer);
                        if (StringUtils.isNotEmpty(writer.toString())) {
                            messages.add(writer.toString());
                        }
                        writer.close();
                    }

                    ErrorAction error = node.getError();
                    if (error != null) {
                        Throwable throwable = error.getError();
                        messages.add(throwable.getMessage());
                    }

                    String nodeName = getLogPrefix(node, nodeWithNames);

                    if (CollectionUtils.isNotEmpty(messages)) {
                        if (logsNodeWise.containsKey(nodeName)) {
                            logsNodeWise.get(nodeName).addAll(messages);
                        } else {
                            logsNodeWise.put(nodeName, messages);
                        }
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Error Sending console Logs", e);
                }
            }
        }
        return logsNodeWise;
    }

    private static String getLogPrefix(FlowNode node, Map<String, String> nodeWithNames) {
        if (nodeWithNames.containsKey(node.getId())) {
            return nodeWithNames.get(node.getId());
        }
        for (FlowNode parentNode : node.getParents()) {
            String prefix = getLogPrefix(parentNode, nodeWithNames);
            if (prefix != null) {
                return prefix;
            }
        }
        return null;
    }
}
