ALTER TABLE structures ADD COLUMN group_name_prefix   VARCHAR(255);
ALTER TABLE structures ADD COLUMN next_group_number  INT NOT NULL DEFAULT 1;
ALTER TABLE structures ADD COLUMN group_profile_pic_url TEXT;
