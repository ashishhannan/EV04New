-- V1 initial schema
CREATE TABLE device (
  id varchar(64) PRIMARY KEY,
  last_seen timestamp,
  connected boolean
);

CREATE TABLE geofence (
  id bigint PRIMARY KEY,
  device_id varchar(64),
  name varchar(255),
  payload bytea,
  created_at timestamp DEFAULT now()
);

CREATE TABLE command_log (
  id bigint PRIMARY KEY,
  device_id varchar(64),
  command_id integer,
  payload bytea,
  created_at timestamp DEFAULT now()
);

CREATE TABLE pending_command (
  id bigint PRIMARY KEY,
  device_id varchar(64),
  payload bytea,
  created_at timestamp DEFAULT now()
);
