-- Run once (or let the app create it via createDatabaseIfNotExist=true)
CREATE DATABASE IF NOT EXISTS hospitaldb;
USE hospitaldb;

-- Table is auto-created by Hibernate (ddl-auto=update); DDL shown for reference:
CREATE TABLE IF NOT EXISTS patient (
  id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name           VARCHAR(100) NOT NULL,
  age            INT          NOT NULL,
  diagnosis      VARCHAR(255),
  admission_date DATE
);

-- One-to-one with patient: UNIQUE(patient_id) allows at most one record per patient.
CREATE TABLE IF NOT EXISTS medical_record (
  id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  blood_group       VARCHAR(5)   NOT NULL,
  allergies         VARCHAR(255),
  emergency_contact VARCHAR(100),
  patient_id        BIGINT       NOT NULL,
  CONSTRAINT uk_medical_record_patient UNIQUE (patient_id),
  CONSTRAINT fk_medical_record_patient FOREIGN KEY (patient_id) REFERENCES patient (id)
);
