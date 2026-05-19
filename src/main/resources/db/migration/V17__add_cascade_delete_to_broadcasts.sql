-- message_broadcasts.structure_id → structures(id)  ON DELETE CASCADE
DO $$
DECLARE _c text;
BEGIN
    SELECT conname INTO _c FROM pg_constraint
    WHERE conrelid  = 'message_broadcasts'::regclass
      AND confrelid = 'structures'::regclass
      AND contype   = 'f';
    IF _c IS NOT NULL THEN
        EXECUTE format('ALTER TABLE message_broadcasts DROP CONSTRAINT %I', _c);
    END IF;
END$$;

ALTER TABLE message_broadcasts
    ADD CONSTRAINT message_broadcasts_structure_id_fk
    FOREIGN KEY (structure_id) REFERENCES structures(id) ON DELETE CASCADE;

-- broadcast_group_results.broadcast_id → message_broadcasts(id)  ON DELETE CASCADE
DO $$
DECLARE _c text;
BEGIN
    SELECT conname INTO _c FROM pg_constraint
    WHERE conrelid  = 'broadcast_group_results'::regclass
      AND confrelid = 'message_broadcasts'::regclass
      AND contype   = 'f';
    IF _c IS NOT NULL THEN
        EXECUTE format('ALTER TABLE broadcast_group_results DROP CONSTRAINT %I', _c);
    END IF;
END$$;

ALTER TABLE broadcast_group_results
    ADD CONSTRAINT broadcast_group_results_broadcast_id_fk
    FOREIGN KEY (broadcast_id) REFERENCES message_broadcasts(id) ON DELETE CASCADE;

-- broadcast_group_results.group_id → whatsapp_groups(id)  ON DELETE CASCADE
DO $$
DECLARE _c text;
BEGIN
    SELECT conname INTO _c FROM pg_constraint
    WHERE conrelid  = 'broadcast_group_results'::regclass
      AND confrelid = 'whatsapp_groups'::regclass
      AND contype   = 'f';
    IF _c IS NOT NULL THEN
        EXECUTE format('ALTER TABLE broadcast_group_results DROP CONSTRAINT %I', _c);
    END IF;
END$$;

ALTER TABLE broadcast_group_results
    ADD CONSTRAINT broadcast_group_results_group_id_fk
    FOREIGN KEY (group_id) REFERENCES whatsapp_groups(id) ON DELETE CASCADE;
