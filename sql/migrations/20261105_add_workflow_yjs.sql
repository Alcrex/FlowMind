-- Select the existing FlowMind database before running this script.
-- Add only missing collaboration columns; preserve all existing rows and Yjs state.
SET @flowmind_yjs_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'wf_workflow' AND column_name = 'yjs_state'),
    'SELECT 1',
    'ALTER TABLE `wf_workflow` ADD COLUMN `yjs_state` MEDIUMBLOB DEFAULT NULL COMMENT ''当前画布的 Yjs 全量状态'''
);
PREPARE flowmind_yjs_statement FROM @flowmind_yjs_ddl;
EXECUTE flowmind_yjs_statement;
DEALLOCATE PREPARE flowmind_yjs_statement;

SET @flowmind_yjs_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'wf_workflow' AND column_name = 'yjs_version'),
    'SELECT 1',
    'ALTER TABLE `wf_workflow` ADD COLUMN `yjs_version` BIGINT NOT NULL DEFAULT 0 COMMENT ''协同状态版本，每次覆盖加一'''
);
PREPARE flowmind_yjs_statement FROM @flowmind_yjs_ddl;
EXECUTE flowmind_yjs_statement;
DEALLOCATE PREPARE flowmind_yjs_statement;
SET @flowmind_yjs_ddl = NULL;
