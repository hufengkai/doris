// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.statistics;

import org.apache.doris.common.io.Text;
import org.apache.doris.common.io.Writable;
import org.apache.doris.statistics.util.InternalQueryResult.ResultRow;
import org.apache.doris.statistics.util.StatisticsUtil;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringJoiner;

public class AnalysisInfo implements Writable {

    private static final Logger LOG = LogManager.getLogger(AnalysisInfo.class);

    public enum AnalysisMode {
        INCREMENTAL,
        FULL
    }

    public enum AnalysisMethod {
        SAMPLE,
        FULL
    }

    public enum AnalysisType {
        FUNDAMENTALS,
        INDEX,
        HISTOGRAM
    }

    public enum JobType {
        // submit by user directly
        MANUAL,
        // submit by system automatically
        SYSTEM
    }

    public enum ScheduleType {
        ONCE,
        PERIOD,
        AUTOMATIC
    }

    public final long jobId;

    public final long taskId;

    public final String catalogName;

    public final String dbName;

    public final String tblName;

    public final Map<String, Set<String>> colToPartitions;

    public final String colName;

    public final long indexId;

    public final JobType jobType;

    public final AnalysisMode analysisMode;

    public final AnalysisMethod analysisMethod;

    public final AnalysisType analysisType;

    public final int samplePercent;

    public final int sampleRows;

    public final int maxBucketNum;

    public final long periodTimeInMs;

    // finished or failed
    public long lastExecTimeInMs;

    public AnalysisState state;

    public final ScheduleType scheduleType;

    public String message;

    // True means this task is a table level task for external table.
    // This kind of task is mainly to collect the number of rows of a table.
    public boolean externalTableLevelTask;

    public AnalysisInfo(long jobId, long taskId, String catalogName, String dbName, String tblName,
            Map<String, Set<String>> colToPartitions, String colName, Long indexId, JobType jobType,
            AnalysisMode analysisMode, AnalysisMethod analysisMethod, AnalysisType analysisType,
            int samplePercent, int sampleRows, int maxBucketNum, long periodTimeInMs, String message,
            long lastExecTimeInMs, AnalysisState state, ScheduleType scheduleType, boolean isExternalTableLevelTask) {
        this.jobId = jobId;
        this.taskId = taskId;
        this.catalogName = catalogName;
        this.dbName = dbName;
        this.tblName = tblName;
        this.colToPartitions = colToPartitions;
        this.colName = colName;
        this.indexId = indexId;
        this.jobType = jobType;
        this.analysisMode = analysisMode;
        this.analysisMethod = analysisMethod;
        this.analysisType = analysisType;
        this.samplePercent = samplePercent;
        this.sampleRows = sampleRows;
        this.maxBucketNum = maxBucketNum;
        this.periodTimeInMs = periodTimeInMs;
        this.message = message;
        this.lastExecTimeInMs = lastExecTimeInMs;
        this.state = state;
        this.scheduleType = scheduleType;
        this.externalTableLevelTask = isExternalTableLevelTask;
    }

    @Override
    public String toString() {
        StringJoiner sj = new StringJoiner("\n", getClass().getName() + ":\n", "\n");
        sj.add("JobId: " + jobId);
        sj.add("CatalogName: " + catalogName);
        sj.add("DBName: " + dbName);
        sj.add("TableName: " + tblName);
        sj.add("ColumnName: " + colName);
        sj.add("TaskType: " + analysisType.toString());
        sj.add("TaskMode: " + analysisMode.toString());
        sj.add("TaskMethod: " + analysisMethod.toString());
        sj.add("Message: " + message);
        sj.add("CurrentState: " + state.toString());
        if (samplePercent > 0) {
            sj.add("SamplePercent: " + samplePercent);
        }
        if (sampleRows > 0) {
            sj.add("SampleRows: " + sampleRows);
        }
        if (maxBucketNum > 0) {
            sj.add("MaxBucketNum: " + maxBucketNum);
        }
        if (colToPartitions != null) {
            sj.add("colToPartitions: " + getColToPartitionStr());
        }
        if (lastExecTimeInMs > 0) {
            sj.add("LastExecTime: " + StatisticsUtil.getReadableTime(lastExecTimeInMs));
        }
        if (periodTimeInMs > 0) {
            sj.add("periodTimeInMs: " + StatisticsUtil.getReadableTime(periodTimeInMs));
        }
        return sj.toString();
    }

    public AnalysisState getState() {
        return state;
    }

    public boolean isJob() {
        return taskId == -1;
    }

    // TODO: use thrift
    public static AnalysisInfo fromResultRow(ResultRow resultRow) {
        try {
            AnalysisInfoBuilder analysisInfoBuilder = new AnalysisInfoBuilder();
            long jobId = Long.parseLong(resultRow.getColumnValue("job_id"));
            analysisInfoBuilder.setJobId(jobId);
            long taskId = Long.parseLong(resultRow.getColumnValue("task_id"));
            analysisInfoBuilder.setTaskId(taskId);
            String catalogName = resultRow.getColumnValue("catalog_name");
            analysisInfoBuilder.setCatalogName(catalogName);
            String dbName = resultRow.getColumnValue("db_name");
            analysisInfoBuilder.setDbName(dbName);
            String tblName = resultRow.getColumnValue("tbl_name");
            analysisInfoBuilder.setTblName(tblName);
            String colName = resultRow.getColumnValue("col_name");
            analysisInfoBuilder.setColName(colName);
            long indexId = Long.parseLong(resultRow.getColumnValue("index_id"));
            analysisInfoBuilder.setIndexId(indexId);
            String partitionNames = resultRow.getColumnValue("col_partitions");
            Map<String, Set<String>> colToPartitions = getColToPartition(partitionNames);
            analysisInfoBuilder.setColToPartitions(colToPartitions);
            String jobType = resultRow.getColumnValue("job_type");
            analysisInfoBuilder.setJobType(JobType.valueOf(jobType));
            String analysisType = resultRow.getColumnValue("analysis_type");
            analysisInfoBuilder.setAnalysisType(AnalysisType.valueOf(analysisType));
            String analysisMode = resultRow.getColumnValue("analysis_mode");
            analysisInfoBuilder.setAnalysisMode(AnalysisMode.valueOf(analysisMode));
            String analysisMethod = resultRow.getColumnValue("analysis_method");
            analysisInfoBuilder.setAnalysisMethod(AnalysisMethod.valueOf(analysisMethod));
            String scheduleType = resultRow.getColumnValue("schedule_type");
            analysisInfoBuilder.setScheduleType(ScheduleType.valueOf(scheduleType));
            String state = resultRow.getColumnValue("state");
            analysisInfoBuilder.setState(AnalysisState.valueOf(state));
            String samplePercent = resultRow.getColumnValue("sample_percent");
            analysisInfoBuilder.setSamplePercent(StatisticsUtil.convertStrToInt(samplePercent));
            String sampleRows = resultRow.getColumnValue("sample_rows");
            analysisInfoBuilder.setSampleRows(StatisticsUtil.convertStrToInt(sampleRows));
            String maxBucketNum = resultRow.getColumnValue("max_bucket_num");
            analysisInfoBuilder.setMaxBucketNum(StatisticsUtil.convertStrToInt(maxBucketNum));
            String periodTimeInMs = resultRow.getColumnValue("period_time_in_ms");
            analysisInfoBuilder.setPeriodTimeInMs(StatisticsUtil.convertStrToInt(periodTimeInMs));
            String lastExecTimeInMs = resultRow.getColumnValue("last_exec_time_in_ms");
            analysisInfoBuilder.setLastExecTimeInMs(StatisticsUtil.convertStrToLong(lastExecTimeInMs));
            String message = resultRow.getColumnValue("message");
            analysisInfoBuilder.setMessage(message);
            return analysisInfoBuilder.build();
        } catch (Exception e) {
            LOG.warn("Failed to deserialize analysis task info.", e);
            return null;
        }
    }

    public String getColToPartitionStr() {
        if (colToPartitions == null || colToPartitions.isEmpty()) {
            return "";
        }
        Gson gson = new Gson();
        return gson.toJson(colToPartitions);
    }

    private static Map<String, Set<String>> getColToPartition(String colToPartitionStr) {
        if (colToPartitionStr == null || colToPartitionStr.isEmpty()) {
            return null;
        }
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, Set<String>>>() {}.getType();
        return gson.fromJson(colToPartitionStr, type);
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeLong(jobId);
        out.writeLong(taskId);
        Text.writeString(out, catalogName);
        Text.writeString(out, dbName);
        Text.writeString(out, tblName);
        out.writeInt(colToPartitions.size());
        for (Entry<String, Set<String>> entry : colToPartitions.entrySet()) {
            Text.writeString(out, entry.getKey());
            out.writeInt(entry.getValue().size());
            for (String part : entry.getValue()) {
                Text.writeString(out, part);
            }
        }
        Text.writeString(out, colName);
        out.writeLong(indexId);
        Text.writeString(out, jobType.toString());
        Text.writeString(out, analysisMode.toString());
        Text.writeString(out, analysisMethod.toString());
        Text.writeString(out, analysisType.toString());
        out.writeInt(samplePercent);
        out.writeInt(sampleRows);
        out.writeInt(maxBucketNum);
        out.writeLong(periodTimeInMs);
        out.writeLong(lastExecTimeInMs);
        Text.writeString(out, state.toString());
        Text.writeString(out, scheduleType.toString());
        Text.writeString(out, message);
        out.writeBoolean(externalTableLevelTask);
    }

    public static AnalysisInfo read(DataInput dataInput) throws IOException {
        AnalysisInfoBuilder analysisInfoBuilder = new AnalysisInfoBuilder();
        analysisInfoBuilder.setJobId(dataInput.readLong());
        long taskId = dataInput.readLong();
        analysisInfoBuilder.setTaskId(taskId);
        analysisInfoBuilder.setCatalogName(Text.readString(dataInput));
        analysisInfoBuilder.setDbName(Text.readString(dataInput));
        analysisInfoBuilder.setTblName(Text.readString(dataInput));
        int size = dataInput.readInt();
        Map<String, Set<String>> colToPartitions = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String k = Text.readString(dataInput);
            int partSize = dataInput.readInt();
            Set<String> parts = new HashSet<>();
            for (int j = 0; j < partSize; j++) {
                parts.add(Text.readString(dataInput));
            }
            colToPartitions.put(k, parts);
        }
        analysisInfoBuilder.setColToPartitions(colToPartitions);
        analysisInfoBuilder.setColName(Text.readString(dataInput));
        analysisInfoBuilder.setIndexId(dataInput.readLong());
        analysisInfoBuilder.setJobType(JobType.valueOf(Text.readString(dataInput)));
        analysisInfoBuilder.setAnalysisMode(AnalysisMode.valueOf(Text.readString(dataInput)));
        analysisInfoBuilder.setAnalysisMethod(AnalysisMethod.valueOf(Text.readString(dataInput)));
        analysisInfoBuilder.setAnalysisType(AnalysisType.valueOf(Text.readString(dataInput)));
        analysisInfoBuilder.setSamplePercent(dataInput.readInt());
        analysisInfoBuilder.setSampleRows(dataInput.readInt());
        analysisInfoBuilder.setMaxBucketNum(dataInput.readInt());
        analysisInfoBuilder.setPeriodTimeInMs(dataInput.readLong());
        analysisInfoBuilder.setLastExecTimeInMs(dataInput.readLong());
        analysisInfoBuilder.setState(AnalysisState.valueOf(Text.readString(dataInput)));
        analysisInfoBuilder.setScheduleType(ScheduleType.valueOf(Text.readString(dataInput)));
        analysisInfoBuilder.setMessage(Text.readString(dataInput));
        analysisInfoBuilder.setExternalTableLevelTask(dataInput.readBoolean());
        return analysisInfoBuilder.build();
    }
}
