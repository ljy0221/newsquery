-- Phase 5.1: Notification features table additions
-- Inserts default notification rule configurations
INSERT INTO notification_rule_configs (id, rule_type, enabled, condition_json, created_at, updated_at)
VALUES
    ('rule-performance-1', 'PERFORMANCE', true, '{"threshold_multiplier": 1.5, "baseline_ms": 30}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('rule-error-1', 'ERROR', true, '{"enabled": true}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('rule-keyword-1', 'KEYWORD', true, '{"enabled": true}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
