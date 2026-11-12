UPDATE ticketing.outbox_events SET next_attempt_at=created_at WHERE next_attempt_at IS NULL;
ALTER TABLE ticketing.outbox_events ALTER COLUMN next_attempt_at SET DEFAULT clock_timestamp();
ALTER TABLE ticketing.outbox_events ALTER COLUMN next_attempt_at SET NOT NULL;
CREATE TABLE ticketing.inbox_events (
 consumer varchar(100) NOT NULL, event_id uuid NOT NULL,
 aggregate_id uuid NOT NULL, aggregate_version bigint NOT NULL CHECK(aggregate_version>0),
 envelope_hash varchar(64) NOT NULL CHECK(length(envelope_hash)=64),
 processed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(consumer,event_id)
);
CREATE INDEX inbox_aggregate_idx ON ticketing.inbox_events(aggregate_id,consumer);
CREATE TABLE ticketing.event_projections (
 aggregate_id uuid PRIMARY KEY, last_applied_version bigint NOT NULL DEFAULT 0 CHECK(last_applied_version>=0),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE ticketing.projection_events (
 aggregate_id uuid NOT NULL REFERENCES ticketing.event_projections(aggregate_id),
 aggregate_version bigint NOT NULL CHECK(aggregate_version>0), event_id uuid NOT NULL UNIQUE,
 event_type varchar(64) NOT NULL, applied_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(aggregate_id,aggregate_version)
);
CREATE TABLE ticketing.message_failures (
 id uuid PRIMARY KEY, consumer varchar(100) NOT NULL, topic varchar(249) NOT NULL,
 partition_id integer NOT NULL CHECK(partition_id>=0), record_offset bigint NOT NULL CHECK(record_offset>=0),
 record_key text, raw_event text, event_id uuid, attempts integer NOT NULL CHECK(attempts=3),
 error varchar(500) NOT NULL, status varchar(16) NOT NULL DEFAULT 'DEAD' CHECK(status IN ('DEAD','RESOLVED')),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(), resolved_at timestamptz,
 dlq_attempts integer NOT NULL DEFAULT 0 CHECK(dlq_attempts BETWEEN 0 AND 3),
 dlq_next_attempt_at timestamptz NOT NULL DEFAULT clock_timestamp(), dlq_published_at timestamptz,
 UNIQUE(consumer,topic,partition_id,record_offset),
 CHECK(status<>'RESOLVED' OR resolved_at IS NOT NULL)
);
CREATE INDEX message_failures_event_idx ON ticketing.message_failures(consumer,event_id) WHERE status='DEAD';
CREATE INDEX message_failures_dlq_idx ON ticketing.message_failures(dlq_next_attempt_at) WHERE dlq_published_at IS NULL AND dlq_attempts<3;
ALTER TABLE ticketing.payment_compensations ADD COLUMN next_attempt_at timestamptz NOT NULL DEFAULT clock_timestamp();
CREATE TABLE ticketing.compensation_attempts (
 compensation_id uuid NOT NULL REFERENCES ticketing.payment_compensations(id),
 attempt integer NOT NULL CHECK(attempt BETWEEN 1 AND 3), outcome varchar(16) NOT NULL CHECK(outcome IN ('FAILED','COMPLETED')),
 attempted_at timestamptz NOT NULL DEFAULT clock_timestamp(), detail varchar(500) NOT NULL,
 PRIMARY KEY(compensation_id,attempt)
);
