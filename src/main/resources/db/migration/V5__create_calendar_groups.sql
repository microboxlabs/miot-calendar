CREATE TABLE cld_calendar_groups (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50)  UNIQUE NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    active      BOOLEAN      DEFAULT true,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_cld_calendar_groups_code   ON cld_calendar_groups(code);
CREATE INDEX idx_cld_calendar_groups_active ON cld_calendar_groups(active);

CREATE TABLE cld_calendar_group_members (
    group_id    UUID NOT NULL REFERENCES cld_calendar_groups(id) ON DELETE CASCADE,
    calendar_id UUID NOT NULL REFERENCES cld_calendars(id)       ON DELETE CASCADE,
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT pk_cld_calendar_group_members PRIMARY KEY (group_id, calendar_id)
);

CREATE INDEX idx_cld_grp_members_group    ON cld_calendar_group_members(group_id);
CREATE INDEX idx_cld_grp_members_calendar ON cld_calendar_group_members(calendar_id);

COMMENT ON TABLE cld_calendar_groups IS 'Flat group labels (tags) for organizing calendars';
COMMENT ON TABLE cld_calendar_group_members IS 'Many-to-many: which calendars belong to which groups';
