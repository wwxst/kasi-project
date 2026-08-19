ALTER TABLE `short_drama_connection`
    ADD COLUMN `filing_mode` VARCHAR(16) NOT NULL DEFAULT 'API'
        COMMENT 'Account filing mode: API or MANUAL';
