-- Enriched forensic seed data for investigation, remediation, and regression testing.
INSERT INTO forensic_cases (
  id, case_number, transaction_id, account_id, source_type, source_reference_id,
  status, priority, title, summary, evidence_completeness, assigned_to, created_by,
  remediation_status, remediation_json, systemic, version, created_at, updated_at
) VALUES (
  '11111111-1111-4111-a111-111111111111', 'FC-20260812-001', 'a0000000-0000-0000-0000-000000000001',
  'b0000000-0000-0000-0000-000000000001', 'INVARIANT', 'INV-REVERSAL-001',
  'PENDING_CHECKER', 'CRITICAL', 'Sự cố Bút toán đảo thiếu tham chiếu gốc (INV-REVERSAL-001)',
  'Hệ thống tự động khởi tạo Hồ sơ Sự cố cho Giao dịch a0000000-0000-0000-0000-000000000001. Cần điều tra xử lý bất bằng sổ cái.',
  'COMPLETE', 'c0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001',
  'IN_PROGRESS',
  '[{"actionType":"ADJUSTMENT_JOURNAL","referenceId":"ADJ-20260812-01","description":"Phát hành Bút toán Điều chỉnh 5.000.000 VND","completed":true,"completedAt":"2026-08-12T10:00:00Z"},{"actionType":"ACCOUNT_HOLD","referenceId":"HOLD-20260812-01","description":"Phong tỏa Tạm thời Tài khoản Thụ hưởng","completed":false}]',
  TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

INSERT INTO forensic_findings (
  id, case_id, finding_key, rule_code, subject_type, subject_id, outcome, severity, disposition,
  title, detail, evidence_json, evidence_hash, occurrence_count, detected_at, last_seen_at, version
) VALUES (
  '22222222-2222-4222-a222-222222222222', '11111111-1111-4111-a111-111111111111',
  'FINDING-INV-001', 'INV-REVERSAL-001', 'TRANSACTION', 'a0000000-0000-0000-0000-000000000001', 'VIOLATION', 'CRITICAL', 'UNRESOLVED',
  'Bút toán đảo thiếu tham chiếu gốc', 'Phát hiện 1 bút toán đảo có số tiền 5.000.000 VND không có tham chiếu giao dịch gốc',
  '{"transactionId":"a0000000-0000-0000-0000-000000000001","amount":5000000}'::jsonb,
  'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
  1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1
) ON CONFLICT (id) DO NOTHING;

INSERT INTO forensic_replay_scenarios (
  scenario_id, title, engine_key, source_incident_id, source_evidence_ref,
  status, definition_json, sanitized, created_by, version, created_at, updated_at
) VALUES (
  'SCENARIO-AUTO-001',
  'Kịch bản Regression Test cho Sự cố Bút toán đảo (FC-20260812-001)', 'DETERMINISTIC',
  '11111111-1111-4111-a111-111111111111', 'a0000000-0000-0000-0000-000000000001',
  'DRAFT',
  '{"schemaVersion":1,"description":"Auto-generated scenario for INV-REVERSAL-001","faults":[{"ruleCode":"INV-REVERSAL-001","type":"LEDGER_IMBALANCE"}]}'::jsonb,
  TRUE, 'c0000000-0000-0000-0000-000000000001', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (scenario_id) DO NOTHING;
