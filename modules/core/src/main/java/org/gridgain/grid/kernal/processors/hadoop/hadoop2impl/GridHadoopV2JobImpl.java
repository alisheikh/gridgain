/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.hadoop.hadoop2impl;

import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.task.*;
import org.gridgain.grid.*;
import org.gridgain.grid.hadoop.*;
import org.gridgain.grid.kernal.processors.hadoop.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Hadoop job implementation for v2 API.
 */
public class GridHadoopV2JobImpl implements GridHadoopJob {
    /** Hadoop job ID. */
    private GridHadoopJobId jobId;

    /** Job info. */
    protected GridHadoopDefaultJobInfo jobInfo;

    /** Hadoop native job context. */
    protected JobContext ctx;

    /** Key class. */
    private Class<?> keyCls;

    /** Value class. */
    private Class<?> valCls;

    /**
     * @param jobId Job ID.
     * @param jobInfo Job info.
     */
    public GridHadoopV2JobImpl(GridHadoopJobId jobId, GridHadoopDefaultJobInfo jobInfo) {
        this.jobId = jobId;
        this.jobInfo = jobInfo;

        ctx = new JobContextImpl(jobInfo.configuration(), new JobID(jobId.globalId().toString(), jobId.localId()));

        keyCls = ctx.getMapOutputKeyClass();
        valCls = ctx.getMapOutputValueClass();
    }

    /** {@inheritDoc} */
    @Override public GridHadoopJobId id() {
        return jobId;
    }

    /** {@inheritDoc} */
    @Override public GridHadoopJobInfo info() {
        return jobInfo;
    }

    /** {@inheritDoc} */
    @Override public Collection<GridHadoopFileBlock> input() throws GridException {
        return GridHadoopV2Splitter.splitJob(ctx);
    }

    /** {@inheritDoc} */
    @Override public int reducers() {
        return ctx.getNumReduceTasks();
    }

    /** {@inheritDoc} */
    @Override public GridHadoopPartitioner partitioner() throws GridException {
        try {
            Class<? extends Partitioner> partCls = ctx.getPartitionerClass();

            return new GridHadoopV2PartitionerAdapter((Partitioner<Object, Object>)U.newInstance(partCls));
        }
        catch (ClassNotFoundException e) {
            throw new GridException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public GridHadoopTask createTask(GridHadoopTaskInfo taskInfo) {
        switch (taskInfo.type()) {
            case MAP:
                return new GridHadoopV2MapTask(taskInfo);

            case REDUCE:
                return new GridHadoopV2ReduceTask(taskInfo);

            case COMBINE:
                return new GridHadoopV2CombineTask(taskInfo);

            case COMMIT:
                return new GridHadoopV2CleanupTask(taskInfo, false);

            case ABORT:
                return new GridHadoopV2CleanupTask(taskInfo, true);

            default:
                return null;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean hasCombiner() {
        return combinerClass() != null;
    }

    /**
     * Gets combiner class.
     *
     * @return Combiner class or {@code null} if combiner is not specified.
     */
    private Class<? extends Reducer<?, ?, ?, ?>> combinerClass() {
        try {
            return ctx.getCombinerClass();
        }
        catch (ClassNotFoundException e) {
            // TODO check combiner class at initialization and throw meaningful exception.
            throw new GridRuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public GridHadoopSerialization keySerialization() throws GridException {
        return new GridHadoopWritableSerialization((Class<? extends Writable>)keyCls);
    }

    /** {@inheritDoc} */
    @Override public GridHadoopSerialization valueSerialization() throws GridException {
        return new GridHadoopWritableSerialization((Class<? extends Writable>)valCls);
    }

    /** {@inheritDoc} */
    @Nullable @Override public String property(String name) {
        return jobInfo.configuration().get(name);
    }

    /**
     * @param type Task type.
     * @return Hadoop task type.
     */
    private TaskType taskType(GridHadoopTaskType type) {
        switch (type) {
            case MAP:
                return TaskType.MAP;

            case REDUCE:
                return TaskType.REDUCE;

            case COMMIT:
            case ABORT:
                return TaskType.JOB_CLEANUP;

            default:
                return null;
        }
    }

    /**
     * Creates Hadoop attempt ID.
     *
     * @param taskInfo Task info.
     * @return Attempt ID.
     */
    public TaskAttemptID attemptId(GridHadoopTaskInfo taskInfo) {
        TaskID tid = new TaskID(ctx.getJobID(), taskType(taskInfo.type()), taskInfo.taskNumber());

        return new TaskAttemptID(tid, taskInfo.attempt());
    }

    /** Hadoop native job context. */
    public JobContext hadoopJobContext() {
        return ctx;
    }
}
