-- V14: Bảng ticket_check_ins (Phân hệ soát vé Check-in của Staff & Chống quét trùng vé)
CREATE TABLE ticketing.ticket_check_ins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES ticketing.tickets(id) ON DELETE RESTRICT,
    event_id UUID NOT NULL REFERENCES ticketing.events(id) ON DELETE RESTRICT,
    staff_user_id UUID NOT NULL REFERENCES ticketing.users(id) ON DELETE RESTRICT,
    gate_name VARCHAR(64) NOT NULL DEFAULT 'MAIN_GATE',
    device_info TEXT,
    checked_in_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_ticket_checked_in UNIQUE (ticket_id)
);

CREATE INDEX idx_check_ins_event ON ticketing.ticket_check_ins(event_id, checked_in_at);
CREATE INDEX idx_check_ins_staff ON ticketing.ticket_check_ins(staff_user_id, checked_in_at);
