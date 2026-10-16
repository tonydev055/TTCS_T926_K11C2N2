BEGIN;
-- S4-05: duyệt đơn vượt hạn mức hoặc bán dưới giá sàn. Chạy sau sprint4-stock-availability.sql.
-- Ba hành động của Quản lý kinh doanh (quyền orders.approve):
--   APPROVE (Duyệt)       → đơn sang Đã duyệt và giữ chỗ tồn;
--   REJECT  (Từ chối)     → đơn sang Từ chối, bắt buộc ý kiến;
--   RETURN  (Trả lại sửa) → đơn về Nháp cho nhân viên sửa rồi gửi lại, bắt buộc ý kiến.
-- Lịch sử duyệt chỉ được thêm mới: không sửa, không xoá.
CREATE TABLE IF NOT EXISTS order_approvals (
 id BIGSERIAL PRIMARY KEY,
 order_id BIGINT NOT NULL REFERENCES orders(id),
 action VARCHAR(10) NOT NULL CHECK (action IN ('APPROVE','REJECT','RETURN')),
 comment VARCHAR(1000) NOT NULL DEFAULT '',
 -- Lý do cần duyệt và công nợ tại thời điểm quyết định, để đối chiếu về sau.
 flags JSONB NOT NULL DEFAULT '[]'::jsonb,
 outstanding NUMERIC(18,2),
 credit_limit NUMERIC(18,2),
 payable NUMERIC(18,2) NOT NULL,
 decided_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
 decided_by_name VARCHAR(150) NOT NULL,
 decided_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT order_approvals_comment_required CHECK (action = 'APPROVE' OR length(trim(comment)) > 0)
);
CREATE INDEX IF NOT EXISTS order_approvals_order ON order_approvals(order_id, decided_at, id);

DROP TRIGGER IF EXISTS order_approvals_readonly ON order_approvals;
CREATE TRIGGER order_approvals_readonly BEFORE UPDATE OR DELETE ON order_approvals
 FOR EACH ROW EXECUTE FUNCTION order_history_readonly();
DROP TRIGGER IF EXISTS catalog_audit ON order_approvals;
CREATE TRIGGER catalog_audit AFTER INSERT ON order_approvals
 FOR EACH ROW EXECUTE FUNCTION sprint2_audit();

CREATE INDEX IF NOT EXISTS orders_pending_approval ON orders(submitted_at) WHERE status = 'PENDING_APPROVAL';
COMMIT;
