-- Migration V37: Add narrative_json column to forensic_cases for persistent caching of business narrative

ALTER TABLE forensic_cases
  ADD COLUMN narrative_json TEXT;
