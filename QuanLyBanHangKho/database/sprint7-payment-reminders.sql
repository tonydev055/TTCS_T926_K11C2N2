-- S7-08: chạy sau sprint7-payments.sql và smtp.sql.
BEGIN;
CREATE TABLE IF NOT EXISTS payment_reminder_runs(run_date DATE PRIMARY KEY,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS payment_reminders(
 id BIGSERIAL PRIMARY KEY,invoice_id BIGINT NOT NULL REFERENCES invoices(id),customer_id BIGINT NOT NULL REFERENCES customers(id),
 recipient_user_id BIGINT REFERENCES users(id),reminder_date DATE NOT NULL,kind TEXT NOT NULL CHECK(kind IN('DUE_IN_3_DAYS','OVERDUE')),
 amount NUMERIC(18,2) NOT NULL CHECK(amount>0),message TEXT NOT NULL,read_at TIMESTAMP,
 mail_mode TEXT NOT NULL CHECK(mail_mode IN('DEV','SMTP','MISSING_RECIPIENT')),
 mail_outbox_id BIGINT REFERENCES mail_outbox(id),dev_mailbox_id BIGINT REFERENCES dev_mailbox_messages(id),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE(invoice_id,reminder_date)
);
CREATE INDEX IF NOT EXISTS payment_reminders_user ON payment_reminders(recipient_user_id,id DESC);
COMMIT;
