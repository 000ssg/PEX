# pex-sql-streaming — Code Overview

> Part of the PEX multi-module project. See [pex-sql overview](../docs/CODE_OVERVIEW.md) and [top-level CODE_OVERVIEW.md](../../docs/CODE_OVERVIEW.md).

---

## Purpose

`pex-sql-streaming` adds streaming SQL execution on top of `pex-sql-core`:

- **CREATE STREAM / DROP STREAM** DDL for defining stream schemas
- **INSERT INTO STREAM** for pushing events into a stream
- **Stream SELECT** with window aggregation
- Four window types: **tumbling**, **hopping**, **sliding**, **session**
- **Watermark-based** window closure — late events are discarded once the watermark advances past the window's close time
- Emit strategies: **FINAL** (emit when window closes) and **CHANGES** (emit after every update)
- Stream-to-stream and stream-to-table joins

---

## Package Structure

```
ssg.pex.sql.streaming
├── StreamingPlugin             SPI PexPlugin (load order 220)
├── ast/
│   ├── StreamNode              base AST node for streaming statements
│   ├── CreateStreamNode        schema + properties
│   ├── DropStreamNode
│   ├── InsertIntoStreamNode
│   ├── StreamSelectNode        query + window + emit strategy
│   ├── WindowSpec              type + size + advance + gap parameters
│   └── EmitStrategy            FINAL | CHANGES
└── engine/
    ├── StreamSimulator         top-level coordinator
    ├── StreamSource            event queue per stream name
    ├── StreamEvent             record: streamName, timestamp, Map<String,Object> data
    ├── Window                  base interface
    ├── TumblingWindow          fixed non-overlapping windows
    ├── HoppingWindow           overlapping fixed-size windows
    ├── SlidingWindow           window advances per event
    ├── SessionWindow           gap-based window closure
    ├── WindowManager           lifecycle management for all active windows
    ├── Watermark               tracks event-time progress; closes late windows
    ├── StreamAggregator        computes COUNT/SUM/AVG/MIN/MAX within a window
    └── StreamSqlParser         parses CREATE STREAM, INSERT INTO STREAM, SELECT ... WINDOW
```

---

## Key Design Decision: Event-Time vs Processing-Time

The streaming engine uses **event-time semantics** — each `StreamEvent` carries a `timestamp` field. `Watermark` tracks the maximum event timestamp seen minus a configurable late-arrival tolerance. Windows close when the watermark passes their end time. This models real-world stream processing (similar to Apache Flink or Kafka Streams) rather than processing-time windowing.

---

## Tests

| Test Class | Count | What It Covers |
|------------|-------|----------------|
| StreamingTest | 115 | all window types, watermarks, aggregations, CREATE/DROP/INSERT |
| RealWorldStreamingTest | 45 | time-series analytics, join scenarios, multi-stream |
| **Total** | **160** | |
