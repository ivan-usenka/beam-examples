package org.apache.beam.examples;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.RowCoder;
import org.apache.beam.sdk.extensions.sql.SqlTransform;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.schemas.JavaBeanSchema;
import org.apache.beam.sdk.schemas.Schema;
import org.apache.beam.sdk.schemas.annotations.DefaultSchema;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionTuple;
import org.apache.beam.sdk.values.Row;
import org.apache.beam.sdk.values.TupleTag;

import java.io.Serializable;
import java.util.List;

public class SqlExample {
    public static void main(String[] args) {
        Pipeline p = Pipeline.create();

        Schema logSchema = Schema
                .builder()
                .addInt32Field("depth")
                .addStringField("logName")
                .build();

        Schema formationSchema = Schema
                .builder()
                .addStringField("name")
                .addInt32Field("startDepth")
                .addInt32Field("endDepth")
                .build();

        PCollection<Log> pojoLogs = p
                .apply(Create.of(
                        new Log(5, "Test"),
                        new Log(10, "Test"),
                        new Log(15, "Test"),
                        new Log(20, "Test"))
                );

        PCollection<Row> logs = pojoLogs
                .apply(ParDo.of(new DoFn<Log, Row>() {
                    @ProcessElement
                    public void processElement(ProcessContext c) {
                        Log element = c.element();
                        Row row = Row.withSchema(logSchema)
                                .addValues(element.depth, element.logName)
                                .build();
                        c.output(row);
                    }
                })).setSchema(logSchema, SerializableFunctions.identity(), SerializableFunctions.identity());


        PCollection<Formation> pojoFormations = p
                .apply(Create.of(
                        new Formation("First", 0, 9),
                        new Formation("Second", 10, 16),
                        new Formation("Third", 17, 23)
                ));
        PCollection<Row> formations = pojoFormations
                .apply(ParDo.of(new DoFn<Formation, Row>() {
                    @ProcessElement
                    public void processElement(ProcessContext c) {
                        Formation element = c.element();
                        Row row = Row.withSchema(formationSchema)
                                .addValues(element.name, element.startDepth, element.endDepth)
                                .build();
                        c.output(row);
                    }
                })).setSchema(formationSchema, SerializableFunctions.identity(), SerializableFunctions.identity());

        PCollectionTuple colls =
                PCollectionTuple.of("Logs", logs,
                        "Formations", formations);

        PCollection<Row> result = colls.apply(SqlTransform.query(
                "SELECT * FROM Logs l " +
                        "Join Formations f ON l.depth >= f.startDepth AND l.depth < f.endDepth"));

        result
                .apply(MapElements.via(new SimpleFunction<Row, String>() {
                    @Override
                    public String apply(Row input) {
                        return input.toString();
                    }
                }))
                .apply(TextIO.write().to("sql").withoutSharding());
        p.run().waitUntilFinish();
    }

    @DefaultSchema(JavaBeanSchema.class)
    public static class Log implements Serializable {
        private int depth;
        private String logName;

        public Log() {
        }

        Log(int depth, String logName) {
            this.depth = depth;
            this.logName = logName;
        }

        public int getDepth() {
            return depth;
        }

        public String getLogName() {
            return logName;
        }

        public void setDepth(int depth) {
            this.depth = depth;
        }

        public void setLogName(String logName) {
            this.logName = logName;
        }
    }

    @DefaultSchema(JavaBeanSchema.class)
    public static class Formation implements Serializable {
        private String name;
        private int startDepth;
        private int endDepth;

        public Formation() {
        }

        Formation(String name, int startDepth, int endDepth) {
            this.name = name;
            this.startDepth = startDepth;
            this.endDepth = endDepth;
        }

        public String getName() {
            return name;
        }

        public int getStartDepth() {
            return startDepth;
        }

        public int getEndDepth() {
            return endDepth;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setStartDepth(int startDepth) {
            this.startDepth = startDepth;
        }

        public void setEndDepth(int endDepth) {
            this.endDepth = endDepth;
        }
    }
}
 