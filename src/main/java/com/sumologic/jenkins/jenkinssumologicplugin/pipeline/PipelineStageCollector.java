package com.sumologic.jenkins.jenkinssumologicplugin.pipeline;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Result;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.NotExecutedNodeAction;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.AtomNode;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.support.actions.PauseAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sumo Logic plugin for Jenkins model.
 * <p>
 * Reads the stages of a pipeline run, and the steps directly inside each stage, from the flow graph.
 * <p>
 * A stage is a {@link BlockStartNode} carrying a {@link LabelAction} that is not a {@link ThreadNameAction}
 * (parallel branches are not reported as stages). Stages are reported flat, including nested ones.
 * The steps of a stage are the atom nodes whose nearest enclosing stage it is, up to the first nested stage.
 */
final class PipelineStageCollector {
    /**
     * Declarative Pipeline tags the stage node with this tag; values starting with {@code SKIPPED_} mean the
     * stage did not run (a {@code when} condition, an earlier failure or unstable result, a restart).
     */
    static final String STAGE_STATUS_TAG = "STAGE_STATUS";
    static final String SKIPPED_PREFIX = "SKIPPED_";
    /** Limit on the steps reported for each stage. */
    static final int MAX_CHILD_NODES = 100;
    static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    /** Node ids are numbers, compare them as numbers. */
    static final Comparator<FlowNode> NODE_ID_ORDER =
            Comparator.comparingInt((FlowNode node) -> node.getId().length()).thenComparing(FlowNode::getId);

    private PipelineStageCollector() {
    }

    /** A stage, or a step inside a stage. */
    static final class Node {
        final FlowNode node;
        final String status;
        final long startTime;
        final long duration;
        final long pause;
        /** Not set for a node that did not run, or that was interrupted with NOT_BUILT. */
        @CheckForNull
        final ErrorAction error;
        final List<Node> steps = new ArrayList<>();

        Node(FlowNode node, String status, long startTime, long duration, long pause, @CheckForNull ErrorAction error) {
            this.node = node;
            this.status = status;
            this.startTime = startTime;
            this.duration = duration;
            this.pause = pause;
            this.error = Result.NOT_BUILT.toString().equals(status) ? null : error;
        }
    }

    /**
     * @param execution the flow execution of a run
     * @return the stages, ordered by start time, then by node id
     */
    @NonNull
    static List<Node> collectStages(@NonNull FlowExecution execution) {
        Map<String, BlockStartNode> stages = new HashMap<>();
        Map<String, BlockEndNode<?>> ends = new HashMap<>();
        // first node following each node, used for the end time of stages and steps
        Map<String, FlowNode> successors = new HashMap<>();
        Map<String, Result> worstWarnings = new HashMap<>();
        Map<String, Long> pauses = new HashMap<>();
        Map<String, List<FlowNode>> steps = new HashMap<>();
        // first stage started directly inside each stage
        Map<String, FlowNode> firstNestedStages = new HashMap<>();

        for (FlowNode node : new DepthFirstScanner().allNodes(execution)) {
            for (FlowNode parent : node.getParents()) {
                successors.merge(parent.getId(), node, PipelineStageCollector::earliest);
            }
            if (isStage(node)) {
                stages.put(node.getId(), (BlockStartNode) node);
            } else if (node instanceof BlockEndNode) {
                BlockEndNode<?> end = (BlockEndNode<?>) node;
                if (isStage(end.getStartNode())) {
                    ends.put(end.getStartNode().getId(), end);
                }
            }
            WarningAction warning = node.getPersistentAction(WarningAction.class);
            long pause = PauseAction.getPauseDuration(node);
            BlockStartNode nearestStage = null;
            for (BlockStartNode enclosing : node.getEnclosingBlocks()) {
                if (!isStage(enclosing)) {
                    continue;
                }
                if (nearestStage == null) {
                    nearestStage = enclosing;
                }
                if (warning != null) {
                    worstWarnings.merge(enclosing.getId(), warning.getResult(), PipelineStageCollector::worst);
                }
                if (pause > 0) {
                    pauses.merge(enclosing.getId(), pause, Long::sum);
                }
            }
            if (nearestStage != null) {
                if (isStage(node)) {
                    firstNestedStages.merge(nearestStage.getId(), node, PipelineStageCollector::earliest);
                } else if (node instanceof AtomNode) {
                    steps.computeIfAbsent(nearestStage.getId(), k -> new ArrayList<>()).add(node);
                }
            }
        }

        List<Node> result = new ArrayList<>(stages.size());
        for (BlockStartNode start : stages.values()) {
            String id = start.getId();
            BlockEndNode<?> end = ends.get(id);
            FlowNode afterEnd = end == null ? null : successors.getOrDefault(end.getId(), end);
            Node stage = stage(start, end, afterEnd, worstWarnings.get(id), pauses.getOrDefault(id, 0L));
            List<FlowNode> stageSteps = steps.getOrDefault(id, new ArrayList<>());
            FlowNode firstNestedStage = firstNestedStages.get(id);
            if (firstNestedStage != null) {
                // steps after a nested stage starts are not reported
                stageSteps.removeIf(step -> NODE_ID_ORDER.compare(step, firstNestedStage) > 0);
            }
            stageSteps.sort(NODE_ID_ORDER);
            for (FlowNode step : stageSteps) {
                if (stage.steps.size() >= MAX_CHILD_NODES) {
                    break;
                }
                stage.steps.add(step(step, successors.get(step.getId())));
            }
            result.add(stage);
        }
        result.sort(Comparator.comparingLong((Node stage) -> stage.startTime)
                .thenComparing(stage -> stage.node, NODE_ID_ORDER));
        return result;
    }

    /**
     * Names of the labelled nodes (stages and parallel branches) that ran, used to name the console log
     * of the nodes inside them.
     *
     * @param execution the flow execution of a run
     * @return node id to label
     */
    @NonNull
    static Map<String, String> collectLabels(@NonNull FlowExecution execution) {
        Map<String, String> labels = new HashMap<>();
        for (FlowNode node : new DepthFirstScanner().allNodes(execution)) {
            LabelAction label = node.getPersistentAction(LabelAction.class);
            if (label != null && isExecuted(node)) {
                labels.put(node.getId(), node.getDisplayName());
            }
        }
        return Collections.unmodifiableMap(labels);
    }

    /**
     * @param afterEnd the node following the end of the stage, the stage duration runs until it starts
     */
    private static Node stage(@NonNull BlockStartNode start, @CheckForNull BlockEndNode<?> end,
                              @CheckForNull FlowNode afterEnd, @CheckForNull Result worstWarning, long pause) {
        boolean executed = isExecuted(start);
        String status;
        ErrorAction error = null;
        if (!executed) {
            status = Result.NOT_BUILT.toString();
        } else if (end == null) {
            status = STATUS_IN_PROGRESS;
        } else {
            error = end.getError();
            if (error != null) {
                status = statusOf(error.getError());
            } else if (worstWarning != null) {
                status = worstWarning.toString();
            } else {
                status = Result.SUCCESS.toString();
            }
        }
        long duration = executed ? durationBetween(start, afterEnd) : 0;
        long startTime = NotExecutedNodeAction.isExecuted(start) ? TimingAction.getStartTime(start) : 0;
        return new Node(start, status, startTime, duration, Math.min(pause, duration), error);
    }

    /**
     * @param next the node following the step, the step duration runs until it starts
     */
    private static Node step(@NonNull FlowNode step, @CheckForNull FlowNode next) {
        boolean executed = NotExecutedNodeAction.isExecuted(step);
        String status;
        ErrorAction error = null;
        if (!executed) {
            status = Result.NOT_BUILT.toString();
        } else if (next == null) {
            status = STATUS_IN_PROGRESS;
        } else {
            error = step.getError();
            WarningAction warning = step.getPersistentAction(WarningAction.class);
            if (error != null) {
                status = statusOf(error.getError());
            } else if (warning != null) {
                status = warning.getResult().toString();
            } else {
                status = Result.SUCCESS.toString();
            }
        }
        long duration = executed ? durationBetween(step, next) : 0;
        long pause = Math.min(PauseAction.getPauseDuration(step), duration);
        return new Node(step, status, TimingAction.getStartTime(step), duration, pause, error);
    }

    /**
     * @return the status of a failed node, a {@link FlowInterruptedException} carries its own result
     * (for example ABORTED when a user aborts the build, NOT_BUILT when a milestone cancels it)
     */
    static String statusOf(@CheckForNull Throwable error) {
        if (error instanceof FlowInterruptedException) {
            return ((FlowInterruptedException) error).getResult().toString();
        }
        return Result.FAILURE.toString();
    }

    /**
     * Duration from the start of a node to the start of the node that follows it, or to now if there is none yet.
     */
    private static long durationBetween(@NonNull FlowNode start, @CheckForNull FlowNode end) {
        long startTime = TimingAction.getStartTime(start);
        if (startTime == 0) {
            return 0;
        }
        long endTime = end == null ? System.currentTimeMillis() : TimingAction.getStartTime(end);
        return Math.max(0, endTime - startTime);
    }

    static boolean isStage(FlowNode node) {
        return node instanceof BlockStartNode
                && node.getPersistentAction(LabelAction.class) != null
                && node.getPersistentAction(ThreadNameAction.class) == null;
    }

    /**
     * @return false for a node that did not run, or a Declarative stage that was skipped
     */
    private static boolean isExecuted(FlowNode node) {
        if (!NotExecutedNodeAction.isExecuted(node)) {
            return false;
        }
        TagsAction tags = node.getPersistentAction(TagsAction.class);
        if (tags == null) {
            return true;
        }
        String value = tags.getTagValue(STAGE_STATUS_TAG);
        return value == null || !value.startsWith(SKIPPED_PREFIX);
    }

    private static FlowNode earliest(FlowNode a, FlowNode b) {
        return NODE_ID_ORDER.compare(a, b) <= 0 ? a : b;
    }

    private static Result worst(Result a, Result b) {
        return a.isWorseThan(b) ? a : b;
    }
}
