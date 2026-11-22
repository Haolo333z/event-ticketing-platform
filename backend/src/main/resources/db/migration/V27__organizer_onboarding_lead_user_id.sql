-- V27: Hoan thien luong Onboarding Nha to chuc cho Khach hang (Customer to Organizer Self-Service)
-- Lien ket truc tiep user_id voi contact_leads va mo rong trang thai lead

ALTER TABLE ticketing.contact_leads 
    ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES ticketing.users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_contact_leads_user_id ON ticketing.contact_leads(user_id);

ALTER TABLE ticketing.contact_leads DROP CONSTRAINT IF EXISTS contact_leads_status_check;
ALTER TABLE ticketing.contact_leads ADD CONSTRAINT contact_leads_status_check 
    CHECK (status IN ('NEW', 'CONTACTED', 'CONVERTED', 'CLOSED', 'REJECTED'));
