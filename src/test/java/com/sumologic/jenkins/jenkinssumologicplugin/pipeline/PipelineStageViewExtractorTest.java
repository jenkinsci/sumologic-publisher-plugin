package com.sumologic.jenkins.jenkinssumologicplugin.pipeline;

import com.sumologic.jenkins.jenkinssumologicplugin.model.PipelineStageModel;
import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

public class PipelineStageViewExtractorTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static final String INTERRUPTION = "org.jenkinsci.plugins.workflow.steps.FlowInterruptedException";

    @Test
    public void sequentialStagesAndSteps() throws Exception {
        WorkflowRun run = run("stage('build') { echo 'hello {world}' }\nstage('test') { echo 'testing' }", Result.SUCCESS);

        List<PipelineStageModel> stages = PipelineStageViewExtractor.getPipelineStages(run);

        assertThat(names(stages), contains("build", "test"));
        PipelineStageModel build = stages.get(0);
        assertThat(build.getId(), is(1));
        assertThat(build.getStatus(), is("SUCCESS"));
        assertThat(build.getArguments(), nullValue());
        assertThat(build.getExecutionNode(), is("(master)"));
        assertThat(build.getError(), nullValue());
        assertThat(build.getSteps().size(), is(1));
        assertThat(build.getSteps().get(0), startsWith("StepName - Print Message,StepStatus - SUCCESS,StepDuration - "));
        assertThat(build.getSteps().get(0), containsString(",StepArguments - hello (world),StepExecutedOn - (master),StepPauseDuration - "));
        assertThat(stages.get(1).getId(), is(2));
    }

    @Test
    public void failingStage() throws Exception {
        WorkflowRun run = run("stage('a') { echo 'a' }\nstage('b') { error 'boom' }", Result.FAILURE);

        PipelineStageModel b = stage(PipelineStageViewExtractor.getPipelineStages(run), "b");

        assertThat(b.getStatus(), is("FAILURE"));
        assertThat(b.getError(), is("StageErrorType - hudson.AbortException,StageErrorMessage - boom"));
        assertThat(b.getSteps().get(0), containsString("StepStatus - FAILURE,"));
        assertThat(b.getSteps().get(0), containsString(",StepErrorType - hudson.AbortException,StepErrorMessage - boom,"));
    }

    @Test
    public void interruptedStagesUseTheInterruptionResult() throws Exception {
        for (Result result : Arrays.asList(Result.FAILURE, Result.NOT_BUILT, Result.ABORTED)) {
            WorkflowRun run = run("try {\n"
                    + "  stage('outer') { stage('inner') { echo 'in'; throw new " + INTERRUPTION + "(hudson.model.Result." + result + ") } }\n"
                    + "} catch (e) { echo 'caught' }\n"
                    + "stage('after') { echo 'after' }", Result.SUCCESS);

            List<PipelineStageModel> stages = PipelineStageViewExtractor.getPipelineStages(run);

            assertThat(names(stages), contains("outer", "inner", "after"));
            for (String name : Arrays.asList("outer", "inner")) {
                PipelineStageModel stage = stage(stages, name);
                assertThat(result + " " + name, stage.getStatus(), is(result.toString()));
                if (result == Result.NOT_BUILT) {
                    assertThat(result + " " + name, stage.getError(), nullValue());
                } else {
                    assertThat(result + " " + name, stage.getError(), startsWith("StageErrorType - " + INTERRUPTION + ","));
                }
            }
            assertThat(stage(stages, "after").getStatus(), is("SUCCESS"));
            // the stage ran, so its console log is reported under its own name
            Map<String, Set<String>> logs = PipelineStageViewExtractor.getConsoleLogs(run);
            assertThat(result.toString(), logs, hasKey("inner"));
            assertThat(result.toString(), logs.get("inner"), hasItem("in\n"));
        }
    }

    @Test
    public void unstableAndWarnError() throws Exception {
        WorkflowRun run = run("stage('outer') { stage('u') { unstable 'meh' } }\n"
                + "stage('w') { warnError('warned') { error 'boom' } }\n"
                + "stage('last') { echo 'x' }", Result.UNSTABLE);

        List<PipelineStageModel> stages = PipelineStageViewExtractor.getPipelineStages(run);

        assertThat(stage(stages, "outer").getStatus(), is("UNSTABLE"));
        assertThat(stage(stages, "u").getStatus(), is("UNSTABLE"));
        assertThat(stage(stages, "u").getSteps().get(0), containsString("StepStatus - UNSTABLE,"));
        assertThat(stage(stages, "w").getStatus(), is("UNSTABLE"));
        assertThat(stage(stages, "last").getStatus(), is("SUCCESS"));
    }

    @Test
    public void parallelStages() throws Exception {
        WorkflowRun run = run("stage('p') { parallel x: { stage('x1') { error 'xfail' } }, y: { stage('y1') { echo 'y' } } }",
                Result.FAILURE);

        List<PipelineStageModel> stages = PipelineStageViewExtractor.getPipelineStages(run);

        assertThat(names(stages).get(0), is("p"));
        assertThat(stage(stages, "p").getStatus(), is("FAILURE"));
        assertThat(stage(stages, "x1").getStatus(), is("FAILURE"));
        assertThat(stage(stages, "y1").getStatus(), is("SUCCESS"));
        assertThat(stage(stages, "y1").getSteps().size(), equalTo(1));
    }

    private WorkflowRun run(String script, Result expected) throws Exception {
        WorkflowJob project = j.createProject(WorkflowJob.class);
        project.setDefinition(new CpsFlowDefinition(script, false));
        return j.buildAndAssertStatus(expected, project);
    }

    private static List<String> names(List<PipelineStageModel> stages) {
        return stages.stream().map(PipelineStageModel::getName).collect(Collectors.toList());
    }

    private static PipelineStageModel stage(List<PipelineStageModel> stages, String name) {
        return stages.stream().filter(s -> name.equals(s.getName())).findFirst()
                .orElseThrow(() -> new AssertionError("no stage " + name + " in " + names(stages)));
    }
}
