-- Multi-sheet workbooks: every worksheet of an imported Excel file is stored as its own sheet row
-- (so cells, history, realtime and permissions keep working per tab) and grouped by workbook_id,
-- which is the id of the first tab. Standalone sheets keep workbook_id NULL.
ALTER TABLE sheet
    ADD COLUMN workbook_id BIGINT NULL,
    ADD COLUMN tab_name    VARCHAR(100) NULL,
    ADD COLUMN tab_index   INT NOT NULL DEFAULT 0,
    ADD INDEX idx_sheet_workbook (workbook_id, tab_index);
